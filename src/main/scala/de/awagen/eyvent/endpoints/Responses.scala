package de.awagen.eyvent.endpoints

import spray.json.{DefaultJsonProtocol, JsonFormat, RootJsonFormat}

object Responses {

  case class ResponseContent[T](data: T, errorMessage: String)

  object ResponseContentProtocol extends DefaultJsonProtocol {
    implicit def responseContentFormat[T: JsonFormat]: RootJsonFormat[ResponseContent[T]] = jsonFormat2(ResponseContent.apply[T])
  }

}
