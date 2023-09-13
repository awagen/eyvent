package de.awagen.eyvent.config.di.modules.persistence

import com.softwaremill.tagging
import de.awagen.eyvent.config.AppProperties
import de.awagen.eyvent.config.AppProperties.config.directoryPathSeparator
import de.awagen.eyvent.config.di.modules.Modules.{GCP_MODULE, PersistenceDIModule}
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, GcpGSDirectoryReader, GcpGSFileReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers
import de.awagen.kolibri.storage.io.writer.base.GcpGSFileWriter

class GCPPersistenceModule extends PersistenceDIModule with tagging.Tag[GCP_MODULE] {

  assert(AppProperties.config.gcpGSBucket.isDefined, "no gcp gs bucket defined")
  assert(AppProperties.config.gcpGSBucketPath.isDefined, "no gcp gs bucket path defined")
  assert(AppProperties.config.gcpGSProjectID.isDefined, "no gcp projectID defined")

  lazy val writer: Writers.FileWriter[String, _] = GcpGSFileWriter(
    AppProperties.config.gcpGSBucket.get,
    AppProperties.config.gcpGSBucketPath.get,
    AppProperties.config.gcpGSProjectID.get
  )

  lazy val reader: Reader[String, Seq[String]] = GcpGSFileReader(
    AppProperties.config.gcpGSBucket.get,
    AppProperties.config.gcpGSBucketPath.get,
    AppProperties.config.gcpGSProjectID.get
  )

  val filterNone: String => Boolean = new SerializableFunction1[String, Boolean](){
    override def apply(v1: String): Boolean = true
  }

  override def dataOverviewReader(fileFilter: String => Boolean): DataOverviewReader = GcpGSDirectoryReader(
    AppProperties.config.gcpGSBucket.get,
    AppProperties.config.gcpGSBucketPath.get,
    AppProperties.config.gcpGSProjectID.get,
    directoryPathSeparator,
    fileFilter)


}
