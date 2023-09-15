package de.awagen.eyvent.endpoints

import de.awagen.eyvent.config.AppProperties.config.{acceptAllGroups, eventPartitioning, validGroups}
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
import zio.{Queue, Task, ZIO}


// TODO: we do have the endpoints defined below which place events in queues, yet need to define the
// consume process. Group events per partition identifier and handle rollover for all partial files. Also keep
// the node hash within the file names to avoid collision between distinct nodes
object EventEndpoints {

  case class Event(eventType: String, payload: JsObject, timePlacedInMillis: Long, partitionId: String)

  /**
   * Retrieve the body and validate it against the supposed format.
   * If the validation succeeds, make sure the event is processed.
   */
  private[endpoints] def handleEventBody(req: Request,
                                         eventStructDef: NestedStructDef[Any],
                                         eventType: String,
                                         eventQueue: Queue[Event]): Task[Response] = for {
    jsonObj <- req.body.asString.map(x => x.parseJson.asJsObject)
    // validate by the passed structural definition
    _ <- ZIO.attempt(eventStructDef.cast(jsonObj))
    _ <- eventQueue.offer(Event(eventType, jsonObj, System.currentTimeMillis(), eventPartitioning.partition.apply(jsonObj)))
    // compose response
    response <- ZIO.succeed(Response.json(ResponseContent(true, "").toJson.toString()))
  } yield response

  /**
   * Configure an event posting endpoint where the passed payload is validated against the
   * passed structDef (description how the json is supposed to look, e.g which fields, which type)
   *
   * @param eventType - name of the event type
   * @param eventStructDef - the structural description how a payload is supposed to look
   */
  def eventEndpoint(typeOfEvent: String,
                    eventStructDef: NestedStructDef[Any],
                    eventQueue: Queue[Event]) = Http.collectZIO[Request] {
    case req@Method.POST -> Root / "event" / eventType / group if eventType == typeOfEvent =>
      (for {
        // check whether either group wildcard is configured or the group is known, otherwise ignore the event
        _ <- ZIO.logInfo(s"Received event for group '$group'")
        shouldHandleEvent <- ZIO.succeed(acceptAllGroups || validGroups.contains(group.toUpperCase))
        response <- ZIO.whenCase(shouldHandleEvent) {
          case true => handleEventBody(req, eventStructDef, eventType, eventQueue) @@ countAPIRequestsWithStatus("POST", s"/event/$eventType", group, success = true)
          case false =>
            ZIO.succeed(Response.json(ResponseContent(false, s"Group '$group' not valid, ignoring event").toJson.toString())) @@ countAPIRequestsWithStatus("POST", s"/event/$eventType", "IGNORED", success = false)
        }
      } yield response.get).catchAll(throwable =>
        (ZIO.logWarning(s"""Error on posting event:\nexception: $throwable\ntrace: ${throwable.getStackTrace.mkString("\n")}""")
          *> ZIO.succeed(Response.json(ResponseContent(false, s"Failed posting task").toJson.toString()).withStatus(Status.InternalServerError)))
          @@ countAPIRequestsWithStatus("POST", s"/event/$eventType", group, success = false)
      )
  } @@ cors(corsConfig)

  val sampleStructDef = NestedFieldSeqStructDef(
    Seq(
      FieldDefinitions.FieldDef(StringConstantStructDef("name"), StringStructDef, true),
      FieldDefinitions.FieldDef(StringConstantStructDef("value"), StringStructDef, true),
      FieldDefinitions.FieldDef(StringConstantStructDef("optValue"), StringStructDef, false)
    ),
    Seq.empty
  )

}
