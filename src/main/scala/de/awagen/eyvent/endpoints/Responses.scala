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


package de.awagen.eyvent.endpoints

import spray.json.{DefaultJsonProtocol, JsonFormat, RootJsonFormat}

object Responses {

  case class ResponseContent[T](data: T, errorMessage: String)

  object ResponseContentProtocol extends DefaultJsonProtocol {
    implicit def responseContentFormat[T: JsonFormat]: RootJsonFormat[ResponseContent[T]] = jsonFormat2(ResponseContent.apply[T])
  }

}
