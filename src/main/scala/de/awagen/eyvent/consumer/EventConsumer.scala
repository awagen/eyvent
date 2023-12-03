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


package de.awagen.eyvent.consumer

import de.awagen.eyvent.collections.EventStoreManager
import de.awagen.eyvent.config.AppProperties
import de.awagen.eyvent.config.AppProperties.config.{acceptAllGroups, validGroups}
import de.awagen.eyvent.metrics.Metrics
import de.awagen.kolibri.datatypes.types.JsonStructDefs.NestedStructDef
import spray.json._
import zio.ZIO
import zio.aws.sqs.Sqs
import zio.aws.sqs.model.Message
import zio.sqs.{SqsStream, SqsStreamSettings}
import zio.stream.ZStream

object EventConsumer {

  /**
   * Create stream for consumption of messages from Sqs
   *
   * @param queueUrl
   * @return
   */
  def createSQSEventConsumer(queueUrl: String,
                             waitTimeSeconds: Int = 3,
                             maxNrMessages: Int = 100): ZStream[Sqs, Throwable, Message.ReadOnly] = {
    SqsStream(
      queueUrl,
      SqsStreamSettings(
        stopWhenQueueEmpty = false,
        waitTimeSeconds = Some(waitTimeSeconds),
        maxNumberOfMessages = maxNrMessages
      )
    )
  }

  trait QueueConsumer[T] {

    def start: ZIO[T, Throwable, Unit]

  }

  case class SqsConsumer(url: String,
                         typeOfEvent: String,
                         eventStructDef: NestedStructDef[Any],
                         groupKey: String,
                         eventStoreManager: EventStoreManager,
                         waitTimeSeconds: Int = 3,
                         maxNrMessages: Int = 100) extends QueueConsumer[Sqs] {

    val stream: ZStream[Sqs, Throwable, Message.ReadOnly] = createSQSEventConsumer(url, waitTimeSeconds, maxNrMessages)

    private def consumeMessageEffectAndReturnGroupEffect(message: Message.ReadOnly): ZIO[Any, Throwable, String] = {
      for {
        _ <- ZIO.logInfo(s"Received message: ${message}")
        body <- message.getBody.map(x => x.parseJson.asJsObject).mapBoth(e => e.toThrowable, identity)
        group <- ZIO.attempt(body.fields.get(AppProperties.config.awsSQSGroupKey).map(x => x.toString()).getOrElse("UNKNOWN"))
        shouldHandleEvent <- ZIO.succeed(acceptAllGroups || (group.nonEmpty && validGroups.contains(group.toUpperCase)))
        _ <- ZIO.whenCase(shouldHandleEvent) {
          case true =>
            ZIO.attempt(eventStructDef.cast(body))
          case false => ZIO.succeed(true)
        }
        _ <- eventStoreManager.offer(body, group).mapError[Throwable](e => new RuntimeException(e.toString))
      } yield group
    }

    private def safeConsumeMessageAndReturnGroupEffect(message: Message.ReadOnly): ZIO[Any, Nothing, Unit] = {
      for {
        result <- consumeMessageEffectAndReturnGroupEffect(message).either
        _ <- ZIO.whenCase(result)({
          case Right(group) =>
            ZIO.succeed(true) @@
              Metrics.CalculationsWithMetrics.countConsumedSQSMessagesWithStatus(
                sqsUrl = url,
                group = group,
                msgType = typeOfEvent,
                success = true)
          case Left(e) =>
            ZIO.logWarning(s"Failed consuming sqs queue message.\nCause:\n${e.getCause}\nMsg:\n${e.getMessage}\nTrace:${e.getStackTrace.mkString("\n")}") *>
            (ZIO.succeed(true) @@
              Metrics.CalculationsWithMetrics.countConsumedSQSMessagesWithStatus(
                sqsUrl = url,
                group = "UNKNOWN",
                msgType = typeOfEvent,
                success = false))
        })
      } yield ()
    }

    override def start: ZIO[Sqs, Throwable, Unit] = {
      stream.foreach(safeConsumeMessageAndReturnGroupEffect)
    }

  }

}
