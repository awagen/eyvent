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


package de.awagen.eyvent.config.di.modules.persistence

import com.softwaremill.tagging
import de.awagen.eyvent.config.AppProperties._
import de.awagen.eyvent.config.di.modules.Modules.{LOCAL_MODULE, PersistenceDIModule}
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, LocalDirectoryReader, LocalResourceFileReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers.FileWriter
import de.awagen.kolibri.storage.io.writer.base.LocalDirectoryFileWriter


class LocalPersistenceModule extends PersistenceDIModule with tagging.Tag[LOCAL_MODULE] {

  assert(config.localPersistenceWriteBasePath.isDefined, "no local persistence dir defined")

  lazy val writer: FileWriter[String, _] =
    LocalDirectoryFileWriter(directory = config.localPersistenceWriteBasePath.get)

  lazy val reader: Reader[String, Seq[String]] =
    LocalResourceFileReader(
      basePath = config.localPersistenceReadBasePath.get,
      delimiterAndPosition = None,
      fromClassPath = false
    )

  override def dataOverviewReader(fileFilter: String => Boolean): DataOverviewReader = LocalDirectoryReader(
    baseDir = config.localPersistenceReadBasePath.get,
    baseFilenameFilter = fileFilter)


}
