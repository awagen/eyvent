package de.awagen.eyvent

import de.awagen.eyvent.collections.Queueing.{FlushingStore, MEMORY_SIZE_IN_MB_MEASURE_ID, NUM_ELEMENTS_MEASURE_ID}
import de.awagen.eyvent.collections.{Conditions, EventStoreManager}
import de.awagen.eyvent.config.AppProperties.config._
import de.awagen.eyvent.config.NamingPatterns.PartitionDef
import de.awagen.eyvent.config.di.ZioDIConfig
import de.awagen.eyvent.config.{AppProperties, HttpConfig, NamingPatterns}
import de.awagen.eyvent.endpoints.EventEndpoints._
import de.awagen.eyvent.endpoints.MetricEndpoints
import de.awagen.kolibri.datatypes.io.json.JsonStructDefsJsonProtocol.lazyJsonStructDefsFormat
import de.awagen.kolibri.datatypes.types.JsonStructDefs.{NestedStructDef, StructDef}
import de.awagen.kolibri.storage.io.reader.Reader
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import spray.json._
import zio.http.Server
import zio.logging.backend.SLF4J
import zio.metrics.connectors.{MetricsConfig, prometheus}
import zio.metrics.jvm.DefaultJvmMetrics
import zio.stm.{STM, TRef}
import zio.stream.ZStream
import zio.{Executor, Runtime, Schedule, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer, durationInt}

import java.util.concurrent.Executors

object App extends ZIOAppDefault {

  override val bootstrap: ZLayer[Any, Nothing, Unit] = {
    var layer = Runtime.removeDefaultLoggers >>> SLF4J.slf4j
    if (appBlockingPoolThreads > 0) layer = layer >>> Runtime.setBlockingExecutor(Executor.fromJavaExecutor(Executors.newFixedThreadPool(appBlockingPoolThreads)))
    if (appNonBlockingPoolThreads > 0) layer = layer >>> Runtime.setExecutor(Executor.fromJavaExecutor(Executors.newFixedThreadPool(appNonBlockingPoolThreads)))
    layer.asInstanceOf[ZLayer[Any, Nothing, Unit]]
  }

  val combinedLayer = {
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

  def createEventStoreManager(writer: Writer[String, String, _]) = {
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

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    val flushCheckSchedule = Schedule.fixed(10 seconds)
    val effect = for {
      _ <- ZIO.logInfo("Application started!")
      reader <- ZIO.service[Reader[String, Seq[String]]]
      writer <- ZIO.service[Writer[String, String, _]]
      _ <- ZIO.logInfo("Loading event endpoints")

      usedEndpoints <- ZStream.fromIterable(eventEndpointToStructDefMapping.toSeq)
        .mapZIO(x => {
          for {
            structDef <- ZIO.attempt(reader.read(s"$structDefSubFolder/${x._2}").mkString("\n").parseJson.convertTo[StructDef[Any]].asInstanceOf[NestedStructDef[Any]])
            eventStoreManager <- createEventStoreManager(writer)
            _ <- eventStoreManager.checkNeedsFlushingForAllStores.repeat(flushCheckSchedule).forkDaemon
            endpoint <- ZIO.attempt(eventEndpoint(x._1, structDef, eventStoreManager))
          } yield endpoint
        }).runCollect
      _ <- ZIO.logInfo("Loaded event endpoints")
      _ <- zio.http.Server.serve(
        usedEndpoints.foldLeft(MetricEndpoints.prometheusEndpoint)((oldEndpoints, newEndpoint) => {
          oldEndpoints ++ newEndpoint
        })
      )
      _ <- ZIO.logInfo("Application is about to exit!")
    } yield ()
    effect.provide(Server.defaultWithPort(http_server_port) >+> combinedLayer)
  }

}
