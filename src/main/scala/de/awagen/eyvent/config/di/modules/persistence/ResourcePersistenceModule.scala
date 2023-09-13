package de.awagen.eyvent.config.di.modules.persistence

import com.softwaremill.tagging
import de.awagen.eyvent.config.AppProperties
import de.awagen.eyvent.config.di.modules.Modules.{PersistenceDIModule, RESOURCE_MODULE}
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, LocalResourceDirectoryReader, LocalResourceFileReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers
import de.awagen.kolibri.storage.io.writer.Writers.FileWriter

import java.io.IOException

class ResourcePersistenceModule extends PersistenceDIModule with tagging.Tag[RESOURCE_MODULE] {

  override def reader: Reader[String, Seq[String]] = LocalResourceFileReader(
    basePath = AppProperties.config.localResourceReadBasePath.get,
    delimiterAndPosition = None,
    fromClassPath = true)

  override def dataOverviewReader(fileFilter: String => Boolean): DataOverviewReader = LocalResourceDirectoryReader(
    baseDir = AppProperties.config.localResourceReadBasePath.get,
    baseFilenameFilter = fileFilter)

  override def writer: Writers.FileWriter[String, _] = new FileWriter[String, Unit] {
    override def write(data: String, targetIdentifier: String): Either[Exception, Unit] = {
      Left(new IOException("not writing to local resource"))
    }

    // TODO: implement
    override def delete(targetIdentifier: String): Either[Exception, Unit] = ???

    // TODO: implement
    override def copyDirectory(dirPath: String, toDirPath: String): Unit = ???

    // TODO: implement
    override def moveDirectory(dirPath: String, toDirPath: String): Unit = ???

    // TODO: implement
    override def deleteDirectory(dirPath: String): Unit = ???
  }

}
