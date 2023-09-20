package de.awagen.eyvent.collections

import de.awagen.eyvent.collections.Measures._
import de.awagen.eyvent.config.AppProperties
import de.awagen.eyvent.config.NamingPatterns.PartitionDef
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import spray.json.JsObject
import zio.stm.{STM, TRef}
import zio.stream.ZStream
import zio.{Task, UIO, ZIO}

object Queueing {

  val MEMORY_SIZE_IN_MB_MEASURE_ID = "memorySizeInMB"
  val NUM_ELEMENTS_MEASURE_ID = "numElements"
  val LAST_UPDATE_IN_MILLIS_MEASURE_ID = "lastUpdateInMillis"
  val FIRST_UPDATE_IN_MILLIS_MEASURE_ID = "firstUpdateInMillis"

  trait FlushingStore[A] {

    /**
     * Collection of measures that are updated every time an element is added to the queue
     */
    val measures: Map[String, Measure[String, Any]]

    /**
     * Add elements and update all measures.
     */
    def offer(elements: A*): Task[Unit]

    /**
     * Flush content (e.g to persistence)
     */
    def flush: Task[Unit]

  }

  class BaseStringFlushingStore[StoreType, ElementType](store: StoreType,
                                                        addEffect: (StoreType, String) => UIO[Boolean],
                                                        stringifier: ElementType => String,
                                                        resultCollector: StoreType => Task[String],
                                                        partitionDef: PartitionDef,
                                                        writer: Writer[String, String, _]) extends FlushingStore[ElementType] {

    val measures: Map[String, Measure[String, Any]] = Map(
      MEMORY_SIZE_IN_MB_MEASURE_ID -> new StringMemorySizeInMB(),
      NUM_ELEMENTS_MEASURE_ID -> new NumElements[String](),
      LAST_UPDATE_IN_MILLIS_MEASURE_ID -> new LastUpdateTimeInMillis[String](),
      FIRST_UPDATE_IN_MILLIS_MEASURE_ID -> new FirstUpdateTimeInMillis[String](),
    )

    override def offer(elements: ElementType*): Task[Unit] = for {
      _ <- ZStream.fromIterable(elements)
        .foreach(el => {
          val stringified = stringifier(el)
          addEffect(store, stringified) *>
            ZStream.fromIterable(measures.values).foreach(measure => ZIO.attempt(measure.update(stringified)))
        })
    } yield ()

    /**
     * Flush content (e.g to persistence).
     */
    override def flush: Task[Unit] = for {
      flushContent <- resultCollector(store)
      // only flush if there is something to flush
      writeResult <- ZIO.ifZIO(ZIO.succeed(flushContent.trim.nonEmpty))(
        onTrue = ZIO.attemptBlockingIO({
          val identifier = s"""${AppProperties.config.eventStorageSubFolder.stripSuffix("/")}/${partitionDef.group}/${partitionDef.partitionId.stripSuffix("/")}/events-${AppProperties.config.node_hash}-${partitionDef.timeCreatedInMillis.toString}"""
          writer.write(flushContent, identifier)
        }),
        onFalse = ZIO.succeed(Right(()))
      )
      _ <- ZIO.fromEither(writeResult)
    } yield ()
  }

  case class QueueBasedStringFlushingStore(queue: zio.Queue[String],
                                           partitionDef: PartitionDef,
                                           writer: Writer[String, String, _]) extends BaseStringFlushingStore[zio.Queue[String], JsObject](
    queue,
    (q, el) => q.offer(el),
    x => x.toString(),
    store => store.takeAll.map(seq => seq.mkString("\n")),
    partitionDef,
    writer
  )

  case class StringRefFlushingStore(ref: TRef[String],
                                    partitionDef: PartitionDef,
                                    writer: Writer[String, String, _]) extends BaseStringFlushingStore[TRef[String], JsObject](
    ref,
    (ref, str) => STM.atomically(ref.update(oldStr => if (oldStr.isEmpty) str else oldStr + s"\n$str").map(_ => true)),
    x => x.toString(),
    ref => STM.atomically(ref.get),
    partitionDef,
    writer
  )

}
