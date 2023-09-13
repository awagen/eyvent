package de.awagen.eyvent.endpoints

import de.awagen.eyvent.config.HttpConfig.corsConfig
import zio.ZIO
import zio.http._
import zio.http.HttpAppMiddleware.cors
import zio.metrics.connectors.prometheus.PrometheusPublisher

object MetricEndpoints {

  val prometheusEndpoint = Http.collectZIO[Request] {
    case Method.GET -> Root / "metrics" =>
      ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))
  } @@ cors(corsConfig)

}
