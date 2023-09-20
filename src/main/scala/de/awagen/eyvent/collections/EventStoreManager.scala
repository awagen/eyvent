package de.awagen.eyvent.collections

import de.awagen.eyvent.collections.Conditions.Condition
import de.awagen.eyvent.collections.Queueing.{FlushingStore, StringRefFlushingStore}
import de.awagen.eyvent.config.NamingPatterns.{PartitionDef, Partitioning}
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import spray.json.JsObject
import zio.stm.{STM, TRef, ZSTM}
import zio.stream.ZStream
import zio.{Schedule, Task, ZIO, durationInt}

/**
 * Keep track of created stores, one per group and partitioning.
 * If new message comes in for new partitioning, create new.
 * Also checks for each queue whether queue needs closing / flushing
 * and if so, create new and shift the full one to the "old" collection
 * which will be flushed after a few moments of waiting time (in case
 * the old reference was picked someone and events are still coming in.
 *
 * @param fromTimeInMillisFolderPartitioning - partitioning to apply to each message to determine where to write the data (e.g represented by the directory where to store the event store content)
 * @param map                                - Map to map current partitioning to the actual event store
 * @param groupToPartitionDef                - mapping of group to the currently used PartitionDef
 * @param rollStoreCondition                 - providing predicate that determines whether rolling a particular store (e.g represented by a file)
 * @param toBeFlushed                        - Ref to Seq of queues waiting to be flushed (or to be removed if empty)
 * @param writer                             -  writer used to persist the log files
 *
 *                                           Two scenarios:
 *                     - due to natural progression of partitions, no new events will come in for the FlushingStore for certain partitions
 *                     - criteria indicate we need to flush the given flushing store and provide a new store
 *
 *                     Two types of "rolling":
 *                     - one being the base partition of an event (e.g mainly based on time) determining the base folder
 *                       (this one needs checking basically on each event or each N events)
 *                     - the file-partitioning, e.g rolling files within that directory
 *
 * NOTE: after creating the QueueManager instance need to call the init() method, otherwise
 *                                           we will not get a regular replacement and flushing of stores for which no new events flow in
 *
 */
case class EventStoreManager(fromTimeInMillisFolderPartitioning: Partitioning[Long],
                             map: TRef[Map[PartitionDef, FlushingStore[JsObject]]],
                             groupToPartitionDef: TRef[Map[String, PartitionDef]],
                             rollStoreCondition: Condition[String],
                             toBeFlushed: TRef[Set[FlushingStore[JsObject]]],
                             writer: Writer[String, String, _]) {

  /**
   * For given group and timestamp, checks it either a folder (partitioning) or file rolling is needed.
   * If so, updates the mapping group -> PartitionDef and PartitionDef -> FlushingStore[JsObject].
   *
   * Passed timestamp defaults to System.currentTimeInMillis().
   *
   * For a regular schedule on checking whether rolling is needed, we can just call this function
   *
   * Thus when an event comes in, we just need to call this function for the current group and timestamp
   * before continuing to add the event to the current store.
   *
   * Returns the Option[FlushingStore] that is filled if any store was moved to the toBeFlushed collection,
   * otherwise is empty
   */
  private def handlePartitioningForEvent(group: String, timeInMillis: => Long = System.currentTimeMillis()): ZSTM[Any, Throwable, Option[FlushingStore[JsObject]]] = {
    for {
      partitionDefForEvent <- STM.attempt(PartitionDef(fromTimeInMillisFolderPartitioning.partition(timeInMillis), group, timeInMillis))
      // now first check whether we need to switch folders. In that case, we will need to switch no matter what the rollStoreCondition
      // says (need to roll the folders). In case rollStoreCondition holds, it means rolling a file
      currentPartitionOpt <- groupToPartitionDef.get.map(x => x.get(group))
      currentDefStoreMapping <- map.get
      currentStoreOpt <- STM.attempt(currentPartitionOpt.flatMap(partition => currentDefStoreMapping.get(partition)))
      folderRollNeeded <- STM.attempt(currentPartitionOpt.forall(x => x.partitionId != partitionDefForEvent.partitionId))
      fileRollNeeded <- STM.attempt(currentStoreOpt.forall(store => rollStoreCondition.apply(store.measures)))
      rolledStoreOpt <- STM.ifSTM(STM.succeed(folderRollNeeded || fileRollNeeded))(
        onTrue = moveToBeFlushed(group) <* atomicMappingUpdate(partitionDefForEvent, group),
        onFalse = STM.succeed(None)
      )
    } yield rolledStoreOpt
  }


  /**
   * Transaction that updates both the group mapping to a PartitionDef and the mapping of that PartitionDef
   * to the current store for the events
   */
  private def atomicMappingUpdate(partitionDef: PartitionDef, group: String): ZSTM[Any, Nothing, Unit] = {
    for {
      strRef <- TRef.make[String]("")
      _ <- map.update(x => x + (partitionDef -> StringRefFlushingStore(
        strRef,
        partitionDef,
        writer
      )))
      _ <- groupToPartitionDef.update(x => x + (group -> partitionDef))
    } yield ()
  }

  /**
   * For a given group, if any group to partitionDef mapping is set,
   * remove the PartitionDef -> FlushingStore mapping and the group -> PartitionDef mappings and add
   * the FlushingStore to the toBeFlushed collection
   */
  private def moveToBeFlushed(group: String): ZSTM[Any, Nothing, Option[FlushingStore[JsObject]]] = {
    for {
      partitionDefOpt <- groupToPartitionDef.get.map(x => x.get(group))
      storeOpt <- map.get.map(m => partitionDefOpt.flatMap(part => m.get(part)))
      movedStoreOpt <- STM.ifSTM(STM.succeed(partitionDefOpt.nonEmpty && storeOpt.nonEmpty))(
        onTrue = {
          for {
            _ <- toBeFlushed.update(x => x + storeOpt.get)
            _ <- map.update(x => x - partitionDefOpt.get)
            _ <- groupToPartitionDef.update(x => x - group)
          } yield Some(storeOpt.get)
        },
        onFalse = STM.succeed(None)
      )
    } yield movedStoreOpt
  }

  /**
   * Need to schedule this function to run all N minutes. Note that this also changes the following mappings in case
   * the need to roll directory / file for any particular group:
   * - groupId -> PartitionDef
   * - PartitionDef -> FlushingStore
   */
  def checkNeedsFlushingForAllStores: ZIO[Any, Throwable, Unit] = {
    for {
      _ <- ZIO.logDebug("flush check")
      allKnownGroups <- STM.atomically(groupToPartitionDef.get.map(x => x.keySet))
      _ <- ZStream.from(allKnownGroups)
        .mapZIO(group => {
          STM.atomically(handlePartitioningForEvent(group))
        })
        .filter(x => x.nonEmpty)
        .map(x => x.get)
        .mapZIO(store => {
          ZIO.logInfo("Schedules store for flushing") *>
            store.flush.schedule(Schedule.fixed(10 seconds) && Schedule.once)
        })
        .runDrain
    } yield ()
  }

  /**
   * Handling a single event for a given group.
   * Ensures we have the right partitioning set and then adds the event to the correct store.
   */
  def offer(event: JsObject, group: String): ZIO[Any, Serializable, Unit] = {
    val flushSchedule = Schedule.fixed(10 seconds)
    for {
      // check if we need to flush some store
      needsFlushingScheduleOpt <- STM.atomically(handlePartitioningForEvent(group))
      _ <- ZIO.logInfo(s"Need to flush store?: ${needsFlushingScheduleOpt.nonEmpty}")
      // if so, flush
      _ <- ZIO.fromOption(needsFlushingScheduleOpt).forEachZIO(store => store.flush.schedule(flushSchedule && Schedule.once).forkDaemon)
        .ignore
      partitionDef <- STM.atomically(groupToPartitionDef.get.map(x => x(group)))
      _ <- ZIO.logDebug(s"PartitionDef for group '$group': $partitionDef")
      store <- STM.atomically(map.get.map(x => x.get(partitionDef))).map(x => x.get)
      _ <- ZIO.logDebug(s"Store for group '$group': $store")
      _ <- ZIO.logDebug(s"Measure for group '$group': ${store.measures}")
      _ <- store.offer(event)
      storesToBeFlushed <- STM.atomically(toBeFlushed.get)
      _ <- ZIO.logInfo(s"Stores to be flushed: $storesToBeFlushed")
    } yield ()
  }

  def init() = {
    val flushCheckSchedule = Schedule.fixed(10 seconds)
    checkNeedsFlushingForAllStores.repeat(flushCheckSchedule)
  }

}
