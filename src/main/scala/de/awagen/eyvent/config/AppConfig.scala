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
