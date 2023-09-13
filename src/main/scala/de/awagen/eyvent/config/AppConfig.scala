package de.awagen.eyvent.config

import com.softwaremill.macwire.wire
import de.awagen.eyvent.config.di.modules.persistence.PersistenceModule
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.storage.io.reader.DataOverviewReader

import scala.util.matching.Regex

object AppConfig {

  object JsonFormats {

    val dataOverviewReaderWithRegexFilterFunc: Regex => DataOverviewReader = regex => persistenceModule.persistenceDIModule.dataOverviewReaderWithRegexFilter(regex)

    val dataOverviewReaderWithSuffixFilterFunc: SerializableFunction1[String, DataOverviewReader] = new SerializableFunction1[String, DataOverviewReader] {
      override def apply(suffix: String): DataOverviewReader = persistenceModule.persistenceDIModule.dataOverviewReader(x => x.endsWith(suffix))
    }

    val dataOverviewReaderWithConditionFunc: SerializableFunction1[String => Boolean, DataOverviewReader] = new SerializableFunction1[String => Boolean, DataOverviewReader] {
      override def apply(filter: String => Boolean): DataOverviewReader = persistenceModule.persistenceDIModule.dataOverviewReader(filter)
    }

  }

  val persistenceModule: PersistenceModule = wire[PersistenceModule]

}
