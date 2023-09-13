package de.awagen.eyvent.config

import de.awagen.eyvent.config.AppProperties.config.{connectionPoolSizeMax, connectionPoolSizeMin, connectionTTLInSeconds, connectionTimeoutInSeconds}
import zio.http.Header.{AccessControlAllowMethods, AccessControlAllowOrigin}
import zio.http.ZClient.{Config, customized}
import zio.http.internal.middlewares.Cors.CorsConfig
import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientDriver
import zio.http._
import zio.{Trace, ZLayer}
import zio.durationInt

import java.util.concurrent.TimeUnit

object HttpConfig {

  // same as Client.live, but not using a .fresh, which will always provide a new instance
  private lazy val liveHttpClientLayerWithEnv: ZLayer[ZClient.Config with NettyConfig with DnsResolver, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    (NettyClientDriver.live ++ ZLayer.service[DnsResolver]) >>> customized
  }

  private lazy val configLayer = ZLayer.succeed({
    AppProperties.config.connectionPoolType match {
      case "DYNAMIC" =>
        Config.default.withDynamicConnectionPool(connectionPoolSizeMin, connectionPoolSizeMax, zio.Duration(connectionTTLInSeconds, TimeUnit.SECONDS)).connectionTimeout(connectionTimeoutInSeconds seconds)
      case "FIXED" =>
        Config.default.withFixedConnectionPool(connectionPoolSizeMin).connectionTimeout(connectionTimeoutInSeconds seconds)
      case otherType =>
        throw new RuntimeException(s"Unknown connection pool type '$otherType', set to either 'FIXED' or 'DYNAMIC'")
    }
  })

  private lazy val nettyConfigLayer = AppProperties.config.nettyHttpClientThreadsMax match {
    case e if e > 0 =>
      ZLayer.succeed(NettyConfig.default.maxThreads(AppProperties.config.nettyHttpClientThreadsMax))
    case _ => ZLayer.succeed(NettyConfig.default)
  }

  lazy val liveHttpClientLayer: ZLayer[Any, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    (configLayer ++ nettyConfigLayer ++ DnsResolver.default) >>> Client.live // liveHttpClientLayerWithEnv
  }

  val sslConfig = ClientSSLConfig.Default
  val clientConfig = ZClient.Config.default.ssl(sslConfig)

  val corsConfig: CorsConfig = CorsConfig(
    allowedOrigin = _ => Some(AccessControlAllowOrigin.All),
    allowedMethods = AccessControlAllowMethods(Method.GET, Method.POST, Method.PUT, Method.DELETE, Method.OPTIONS),
    allowedHeaders = Header.AccessControlAllowHeaders.All
  )

}
