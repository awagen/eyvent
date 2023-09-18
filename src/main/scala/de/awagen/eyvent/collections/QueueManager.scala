package de.awagen.eyvent.collections

import de.awagen.eyvent.collections.Conditions.Condition
import de.awagen.eyvent.collections.Queueing.{FlushingStore, StringRefFlushingStore}
import de.awagen.eyvent.config.NamingPatterns.{PartitionDef, Partitioning}
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import spray.json.JsObject
import zio.concurrent.ConcurrentMap
import zio.stm.{STM, TRef}
import zio.{Ref, Schedule, Task, ZIO, durationInt}

/**
 * Keep track of created queues, one per group and partitioning.
 * If new message comes in for new partitioning, create new.
 * Also checks for each queue whether queue needs closing / flushing
 * and if so, create new and shift the full one to the "old" collection
 * which will be flushed after a few moments of waiting time (in case
 * the old reference was picked someone and events are still coming in.
 *
 * @param partitioning - partitioning to apply to each message to determine where to write the data
 * @param map - ConcurrentMap to map current partitioning to the actual store (e.g creation: ConcurrentMap.empty[PartitionDef, FlushingStore[JsObject]])
 * @param writer -  writer used to persist the log files
 * @param toBeFlushed - Ref to Seq of queues waiting to be flushed (or to be removed if empty)
 *
 * Two scenarios:
 * - due to natural progression of partitions, no new events will come in for the FlushingStore for certain partitions
 * - criteria indicate we need to flush the given flushing store and provide a new store
 */
case class QueueManager(partitioning: Partitioning,
                        map: ConcurrentMap[PartitionDef, FlushingStore[JsObject]],
                        groupToPartitionDef: ConcurrentMap[String, PartitionDef],
                        rollStoreCondition: Condition[String],
                        toBeFlushed: Ref[Set[FlushingStore[JsObject]]],
                        writer: Writer[String, String, _]) {

  private[collections] def moveCurrentStoreToWaitingForFlushedAndSetNewPartition(event: JsObject, groupId: String, currentPartitionDef: PartitionDef): ZIO[Any, Throwable, Unit] = {
    for {
      newPartitionDef <- ZIO.succeed(PartitionDef(partitioning.partition(event), groupId, System.currentTimeMillis()))
      _ <- groupToPartitionDef.put(groupId, newPartitionDef)
      storeToFlush <- map.remove(currentPartitionDef).map(x => x.get)
      _ <- toBeFlushed.update(set => set + storeToFlush)
      // remove the store to be flushed and flush it after short initial waiting time
      _ <- (ZIO.logInfo("Flushing store") *> storeToFlush.flush *> toBeFlushed.update(set => set - currentPartitionDef)).schedule(Schedule.fixed(10 seconds) && Schedule.once)
    } yield ()
  }

  /**
   * Function that ensures there is a groupId to PartitionDef mapping
   * and checks whether the current PartitionDef for the groupId meets
   * criteria for being rolled. If so, moves the FlushingStore to the toBeFlushed ref
   * with set schedule giving other fibers who picked the store reference
   * to add their events, before the store is flushed to a file.
   * The new set partitioning will be utilized within the offer-call.
   *
   * // TODO: the rolling part might rather be implemented as STM
   * // TODO: even if no new events come in, we need to have a schedule
   * // on each created store such that it will be flushed after exceeding
   * // some time (e.g check on created attribute)
   */
  private[collections] def checkPartitioning(event: JsObject, groupId: String): Task[Unit] = {
    for {
      _ <- ZIO.logInfo("Revisiting current partitioning for groupId")
      _ <- groupToPartitionDef.putIfAbsent(groupId, PartitionDef(partitioning.partition(event), groupId, System.currentTimeMillis()))
      currentPartitionDef <- groupToPartitionDef.get(groupId).map(x => x.get)
      needsRolling <- map.get(currentPartitionDef).map(x => x.exists(y => rollStoreCondition(y.measures)))
      _ <- ZIO.ifZIO(ZIO.succeed(needsRolling))(
        onTrue = moveCurrentStoreToWaitingForFlushedAndSetNewPartition(event, groupId, currentPartitionDef),
        onFalse = ZIO.succeed(())
      )
    } yield ()
  }

  // all of these queues are expected to be flushed by some scheduled event
  // that is set when the queue is moved here (we leave a small gap between
  // removing as target queue and flushing to allow last events to flow in
  // in case the queue reference was already picked
  def offer(event: JsObject, group: String): Task[Unit] = {
    for {
      _ <- checkPartitioning(event, group)
      partitionDef <- groupToPartitionDef.get(group).map(x => x.get)
      strRef <- Ref.make[String]("")
      _ <- map.putIfAbsent(partitionDef, StringRefFlushingStore(
        strRef,
        partitionDef,
        writer
      ))
      _ <- map.get(partitionDef).map(storeOpt => storeOpt.map(store => store.offer(event)))
    } yield ()
  }

}
