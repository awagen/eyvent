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
