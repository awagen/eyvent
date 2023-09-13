package de.awagen.eyvent.config.di.modules

import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers.Writer

import scala.util.matching.Regex

object Modules {

  // ENV_MODULE indicating environment specifics
  trait ENV_MODULE

  // NON_ENV_MODULE indicating a general module, not env-dependent
  trait NON_ENV_MODULE

  trait AWS_MODULE extends ENV_MODULE

  trait GCP_MODULE extends ENV_MODULE

  trait LOCAL_MODULE extends ENV_MODULE

  trait RESOURCE_MODULE extends ENV_MODULE

  trait GENERAL_MODULE extends NON_ENV_MODULE

  trait PersistenceDIModule {

    def writer: Writer[String, String, _]

    def reader: Reader[String, Seq[String]]

    def dataOverviewReader(dataIdentifierFilter: String => Boolean): DataOverviewReader

    def dataOverviewReaderWithRegexFilter(regex: Regex): DataOverviewReader = {
      dataOverviewReader(filename => regex.matches(filename))
    }

    def dataOverviewReaderUnfiltered: DataOverviewReader = {
      dataOverviewReader(_ => true)
    }

  }

}
