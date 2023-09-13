package de.awagen.eyvent

import de.awagen.eyvent.config.AppProperties.config.{appBlockingPoolThreads, appNonBlockingPoolThreads, http_server_port}
import de.awagen.eyvent.config.HttpConfig
import de.awagen.eyvent.config.di.ZioDIConfig
import de.awagen.eyvent.endpoints.EventEndpoints._
import de.awagen.eyvent.endpoints.MetricEndpoints
import zio.http.Server
import zio.logging.backend.SLF4J
import zio.metrics.connectors.{MetricsConfig, prometheus}
import zio.metrics.jvm.DefaultJvmMetrics
import zio.{Executor, Queue, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer, durationInt}

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

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    val effect = for {
      _ <- ZIO.logInfo("Application started!")
      testEventQueue <- Queue.unbounded[Event]
      _ <- zio.http.Server.serve(
        MetricEndpoints.prometheusEndpoint ++
          eventEndpoint("test", sampleStructDef, testEventQueue)
      )
      _ <- ZIO.logInfo("Application is about to exit!")
    } yield ()
    effect.provide(Server.defaultWithPort(http_server_port) >+> combinedLayer)
  }

}
