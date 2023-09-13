package de.awagen.eyvent.config.di

import de.awagen.eyvent.config.AppConfig
import de.awagen.kolibri.storage.io.reader.{DataOverviewReader, Reader}
import de.awagen.kolibri.storage.io.writer.Writers.Writer
import zio.{ULayer, ZLayer}

object ZioDIConfig {

  val writerLayer: ULayer[Writer[String, String, _]] = ZLayer.succeed(AppConfig.persistenceModule.persistenceDIModule.writer)

  val readerLayer: ULayer[Reader[String, Seq[String]]] = ZLayer.succeed(AppConfig.persistenceModule.persistenceDIModule.reader)

  val overviewReaderLayer: ULayer[DataOverviewReader] = ZLayer.succeed(AppConfig.persistenceModule.persistenceDIModule.dataOverviewReaderUnfiltered)


}
