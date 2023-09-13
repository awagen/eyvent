package de.awagen.eyvent.config.di.modules.persistence

import com.softwaremill.tagging
import de.awagen.eyvent.config.AppProperties
import de.awagen.eyvent.config.AppProperties.config.directoryPathSeparator
import de.awagen.eyvent.config.di.modules.Modules.{AWS_MODULE, PersistenceDIModule}
import de.awagen.kolibri.storage.io.reader.{AwsS3DirectoryReader, AwsS3FileReader, DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers.FileWriter
import de.awagen.kolibri.storage.io.writer.base.AwsS3FileWriter


class AwsPersistenceModule extends PersistenceDIModule with tagging.Tag[AWS_MODULE] {

  assert(AppProperties.config.awsS3Bucket.isDefined, "no s3 bucket defined")
  assert(AppProperties.config.awsS3BucketPath.isDefined, "no s3 bucket path defined")
  assert(AppProperties.config.awsS3Region.isDefined, "no s3 bucket region defined")

  override lazy val writer: FileWriter[String, _] = AwsS3FileWriter(
    bucketName = AppProperties.config.awsS3Bucket.get,
    dirPath = AppProperties.config.awsS3BucketPath.get,
    region = AppProperties.config.awsS3Region.get,
    contentType = "text/plain"
  )

  override lazy val reader: Reader[String, Seq[String]] = AwsS3FileReader(
    AppProperties.config.awsS3Bucket.get,
    AppProperties.config.awsS3BucketPath.get,
    AppProperties.config.awsS3Region.get
  )

  override def dataOverviewReader(fileFilter: String => Boolean): DataOverviewReader = AwsS3DirectoryReader(
    AppProperties.config.awsS3Bucket.get,
    AppProperties.config.awsS3BucketPath.get,
    AppProperties.config.awsS3Region.get,
    directoryPathSeparator,
    fileFilter
  )

}
