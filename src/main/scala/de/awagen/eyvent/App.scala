/**
 * Copyright 2023 Andreas Wagenmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package de.awagen.eyvent

import de.awagen.eyvent.collections.EventStores.{FlushingStore, MEMORY_SIZE_IN_MB_MEASURE_ID, NUM_ELEMENTS_MEASURE_ID}
import de.awagen.eyvent.collections.{Conditions, EventStoreManager}
import de.awagen.eyvent.config.AppProperties.config._
import de.awagen.eyvent.config.NamingPatterns.PartitionDef
import de.awagen.eyvent.config.di.ZioDIConfig
import de.awagen.eyvent.config.{AppProperties, ConsumeMode, HttpConfig, NamingPatterns}
import de.awagen.eyvent.consumer.EventConsumer
import de.awagen.eyvent.endpoints.EventEndpoints._
import de.awagen.eyvent.endpoints.MetricEndpoints
import de.awagen.kolibri.datatypes.io.json.JsonStructDefsJsonProtocol.lazyJsonStructDefsFormat
import de.awagen.kolibri.datatypes.types.JsonStructDefs.{NestedStructDef, StructDef}
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import spray.json._
import zio.aws.core.config.{AwsConfig, CommonAwsConfig}
import zio.aws.core.httpclient.HttpClient
import zio.aws.sqs.Sqs
import zio.http.{Client, Server, ZClient}
import zio.logging.backend.SLF4J
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.metrics.connectors.{MetricsConfig, prometheus}
import zio.metrics.jvm.DefaultJvmMetrics
import zio.stm.{STM, TRef}
import zio.stream.ZStream
import zio.{Chunk, Executor, Ref, Runtime, Scope, ULayer, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer, durationInt}

import java.util.concurrent.Executors

object App extends ZIOAppDefault {

  override val bootstrap: ZLayer[Any, Nothing, Unit] = {
    var layer = Runtime.removeDefaultLoggers >>> SLF4J.slf4j
    if (appBlockingPoolThreads > 0) layer = layer >>> Runtime.setBlockingExecutor(Executor.fromJavaExecutor(Executors.newFixedThreadPool(appBlockingPoolThreads)))
    if (appNonBlockingPoolThreads > 0) layer = layer >>> Runtime.setExecutor(Executor.fromJavaExecutor(Executors.newFixedThreadPool(appNonBlockingPoolThreads)))
    layer.asInstanceOf[ZLayer[Any, Nothing, Unit]]
  }

  private def awsConfigLayer: ZLayer[Any, Throwable, AwsConfig] = {
    val awsCommonConfigLayer: ULayer[CommonAwsConfig] = ZLayer.succeed(
      CommonAwsConfig(
        region = Some(AppProperties.config.awsSQSRegion),
        credentialsProvider = DefaultCredentialsProvider.create(),
        endpointOverride = None,
        commonClientConfig = None
      )
    )
    val combinedLayer: ZLayer[Any, Throwable, CommonAwsConfig with HttpClient] = awsCommonConfigLayer >+> zio.aws.netty.NettyHttpClient.default
    combinedLayer >>> zio.aws.core.config.AwsConfig.configured()
  }

  private def sqsLayer: ZLayer[Any, Throwable, Sqs] = awsConfigLayer >>> zio.aws.sqs.Sqs.live

  private val combinedLayer: ZLayer[Any, Throwable, ZClient.Config with Client with Writer[String, String, _] with Reader[String, Seq[String]] with DataOverviewReader with MetricsConfig with PrometheusPublisher with Unit] = {
    ZLayer.succeed(HttpConfig.clientConfig) >+>
      HttpConfig.liveHttpClientLayer >+>
      ZioDIConfig.writerLayer >+>
      ZioDIConfig.readerLayer >+>
      ZioDIConfig.overviewReaderLayer >+>
      // configs for metric backends
      ZLayer.succeed(MetricsConfig(5.seconds)) >+>
      // The prometheus reporting layer
      prometheus.publisherLayer >+>
      prometheus.prometheusLayer >+>
      // Default JVM Metrics
      DefaultJvmMetrics.live.unit
  }

  private def createEventStoreManager(writer: Writer[String, String, _]): ZIO[Any, Throwable, EventStoreManager] = {
    for {
      map <- STM.atomically(TRef.make(Map.empty[PartitionDef, FlushingStore[JsObject]]))
      groupToPartitionDef <- STM.atomically(TRef.make(Map.empty[String, PartitionDef]))
      toBeFlushed <- STM.atomically(TRef.make(Set.empty[FlushingStore[JsObject]]))
      eventStoreManager <- ZIO.attempt(EventStoreManager(
        NamingPatterns.SequentialPartitioning[Long](Seq(
          NamingPatterns.ByYearPartition,
          NamingPatterns.ByMonthOfYearPartition,
          NamingPatterns.ByDayOfMonthPartition,
          NamingPatterns.ByHourOfDayPartition
        ),
          partitionSeparator = "/"),
        map,
        groupToPartitionDef,
        Conditions.orCondition(Seq(
          Conditions.doubleGEQCondition[String](AppProperties.config.eventFileMaxFileSizeInMB, MEMORY_SIZE_IN_MB_MEASURE_ID),
          Conditions.intGEQCondition[String](AppProperties.config.eventFileMaxNumberOfEvents, NUM_ELEMENTS_MEASURE_ID)
        )),
        toBeFlushed,
        writer
      ))
    } yield eventStoreManager
  }

  private[this] var eventStoresManagers: Ref[Seq[EventStoreManager]] = _

  case class EndpointAndStructDefID(endpoint: String, structDefId: String)

  case class EndpointAndStructDef(endpoint: String, structDef: NestedStructDef[Any])

  /**
   * Setup the event store manager instances for each (endpointName, structDefName) combination
   * @return
   */
  private def setupEventStoresManager: ZIO[Any with Reader[String, Seq[String]] with Writer[String, String, _], Throwable, Chunk[(EndpointAndStructDef, EventStoreManager)]] = {
    for {
      reader <- ZIO.service[Reader[String, Seq[String]]]
      writer <- ZIO.service[Writer[String, String, _]]
      usedEndpoints <- ZStream.fromIterable(eventEndpointToStructDefMapping.toSeq).map(x => EndpointAndStructDefID(x._1, x._2))
        .mapZIO(x => {
          for {
            structDef <- ZIO.attempt(reader.read(s"$structDefSubFolder/${x.structDefId}").mkString("\n").parseJson.convertTo[StructDef[Any]].asInstanceOf[NestedStructDef[Any]])
            eventStoreManager <- createEventStoreManager(writer)
            _ <- eventStoreManager.init()
            _ <- eventStoresManagers.update(x => x :+ eventStoreManager)
          } yield (EndpointAndStructDef(x.endpoint, structDef), eventStoreManager)
        }).runCollect
      _ <- ZIO.logInfo("Loaded event endpoints")
    } yield usedEndpoints
  }

  /**
   * Use this to set up the http endpoints for the distinct event types
   * @param endpointDefAndStoreManagerSeq
   * @return
   */
  private def setAndServeEndpoints(endpointDefAndStoreManagerSeq: Seq[(EndpointAndStructDef, EventStoreManager)]): ZIO[Any with Reader[String, Seq[String]] with Writer[String, String, _] with PrometheusPublisher with Server, Throwable, Unit] = {
    for {
      httpEndpoints <- ZStream.fromIterable(endpointDefAndStoreManagerSeq).map(x => eventEndpoint(x._1.endpoint, x._1.structDef, x._2)).runCollect
      _ <- zio.http.Server.serve(
        httpEndpoints.foldLeft(MetricEndpoints.prometheusEndpoint)((oldEndpoints, newEndpoint) => {
          oldEndpoints ++ newEndpoint
        })
      )
    } yield ()
  }

  /**
   * Use this in case consume mode is set to "AWS_SQS", in which case we dont need receiving http endpoints but
   * the respective queue consumers.
   * @param sqsUrl
   * @param endpointDefAndStoreManagerSeq
   * @param groupKey
   * @return
   */
  private def setSqsConsumption(sqsUrl: String,
                                endpointDefAndStoreManagerSeq: Seq[(EndpointAndStructDef, EventStoreManager)],
                                groupKey: String): ZIO[Any, Throwable, Unit] = {
    ZStream.fromIterable(endpointDefAndStoreManagerSeq)
      .foreach(x => {
        ZIO.attempt(EventConsumer.SqsConsumer(
          url = sqsUrl,
          typeOfEvent = x._1.endpoint,
          eventStructDef = x._1.structDef,
          groupKey = groupKey,
          eventStoreManager = x._2,
          waitTimeSeconds = AppProperties.config.awsSQSMaxWaitTimeSeconds,
          maxNrMessages = AppProperties.config.awsSQSMaxNumRequestMessages
        )).forEachZIO(consumer => consumer.start)
      })
      .provide(sqsLayer)
  }

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    val effect = for {
      _ <- ZIO.logInfo("Application started!")
      _ <- ZIO.logInfo("Loading event endpoints")

      // setting up storage of EventStoreManagers such that we can flush all
      // stores when app terminates
      eventStoreMngmtRef <- Ref.make[Seq[EventStoreManager]](Seq.empty)
      _ <- ZIO.attempt({
        eventStoresManagers = eventStoreMngmtRef
      })

      // here decide whether http endpoint is used for direct consumption or messages are pulled from a queue
      endpointAndStructDefs <- setupEventStoresManager
      _ <- ZIO.whenCase(AppProperties.config.consumeMode)({
        case ConsumeMode.HTTP => setAndServeEndpoints(endpointAndStructDefs)
        case ConsumeMode.AWS_SQS =>
          setSqsConsumption(AppProperties.config.awsSQSQueueUrl, endpointAndStructDefs, AppProperties.config.awsSQSGroupKey)
      })
      _ <- ZIO.logInfo("Application is about to exit!")
    } yield ()
    effect.provide(Server.defaultWithPort(http_server_port) >+> combinedLayer)
      // try to flush all remaining data before shutting down
      .onExit(_ => {
        for {
          _ <- ZIO.logInfo("Starting Exit Sequence")
          managers <- eventStoresManagers.get
          _ <- (ZIO.attempt(managers.nonEmpty) && ZIO.logInfo(s"Persisting '${managers.size}' remaining stores before closing down").as(true)).ignore
          _ <- ZStream.fromIterable(managers)
            .mapZIO(mng => mng.flushAllStores.ignore)
            .runDrain
            .ignore
          _ <- ZIO.logInfo("Completed Exit Sequence")
        } yield ()
      })
  }

}
