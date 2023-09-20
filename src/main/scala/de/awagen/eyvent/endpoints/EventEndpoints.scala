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

import de.awagen.eyvent.collections.EventStoreManager
import de.awagen.eyvent.config.AppProperties.config.{acceptAllGroups, validGroups}
import de.awagen.eyvent.config.HttpConfig.corsConfig
import de.awagen.eyvent.endpoints.Responses.ResponseContent
import de.awagen.eyvent.endpoints.Responses.ResponseContentProtocol.responseContentFormat
import de.awagen.eyvent.metrics.Metrics.CalculationsWithMetrics.countAPIRequestsWithStatus
import de.awagen.kolibri.datatypes.types.FieldDefinitions
import de.awagen.kolibri.datatypes.types.JsonStructDefs.{NestedFieldSeqStructDef, NestedStructDef, StringConstantStructDef, StringStructDef}
import spray.json.DefaultJsonProtocol._
import spray.json._
import zio.http.HttpAppMiddleware.cors
import zio.http._
import zio.{Task, ZIO}


object EventEndpoints {

  case class Event(eventType: String, payload: JsObject, timePlacedInMillis: Long, partitionId: String)

  /**
   * Retrieve the body and validate it against the supposed format.
   * If the validation succeeds, make sure the event is processed.
   */
  private[endpoints] def handleEventBody(req: Request,
                                         eventStructDef: NestedStructDef[Any],
                                         group: String,
                                         eventStoreManager: EventStoreManager): ZIO[Any, Serializable, Response] = {
    for {
      jsonObj <- req.body.asString.map(x => x.parseJson.asJsObject)
      // validate by the passed structural definition
      _ <- ZIO.attempt(eventStructDef.cast(jsonObj))
      _ <- eventStoreManager.offer(jsonObj, group)
      // compose response
      response <- ZIO.succeed(Response.json(ResponseContent(true, "").toJson.toString()))
    } yield response
  }

  /**
   * Configure an event posting endpoint where the passed payload is validated against the
   * passed structDef (description how the json is supposed to look, e.g which fields, which type)
   *
   * @param eventType      - name of the event type
   * @param eventStructDef - the structural description how a payload is supposed to look
   */
  def eventEndpoint(typeOfEvent: String,
                    eventStructDef: NestedStructDef[Any],
                    eventStoreManager: EventStoreManager) = Http.collectZIO[Request] {
    case req@Method.POST -> Root / "event" / eventType / group if eventType == typeOfEvent =>
      (for {
        // check whether either group wildcard is configured or the group is known, otherwise ignore the event
        _ <- ZIO.logInfo(s"Received event for group '$group'")
        shouldHandleEvent <- ZIO.succeed(acceptAllGroups || validGroups.contains(group.toUpperCase))
        response <- ZIO.whenCase(shouldHandleEvent) {
          case true => handleEventBody(req, eventStructDef, group, eventStoreManager) @@ countAPIRequestsWithStatus("POST", s"/event/$eventType", group, success = true)
          case false =>
            ZIO.succeed(Response.json(ResponseContent(false, s"Group '$group' not valid, ignoring event").toJson.toString())) @@ countAPIRequestsWithStatus("POST", s"/event/$eventType", "IGNORED", success = false)
        }
      } yield response.get).catchAll(throwable =>
        (ZIO.logWarning(s"""Error on posting event:\nexception = $throwable""")
          *> ZIO.succeed(Response.json(ResponseContent(false, s"Failed posting task").toJson.toString()).withStatus(Status.InternalServerError)))
          @@ countAPIRequestsWithStatus("POST", s"/event/$eventType", group, success = false)
      )
  } @@ cors(corsConfig)

  val sampleStructDef = NestedFieldSeqStructDef(
    Seq(
      FieldDefinitions.FieldDef(StringConstantStructDef("name"), StringStructDef, required = true),
      FieldDefinitions.FieldDef(StringConstantStructDef("value"), StringStructDef, required = true),
      FieldDefinitions.FieldDef(StringConstantStructDef("optValue"), StringStructDef, required = false)
    ),
    Seq.empty
  )

}
