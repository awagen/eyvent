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
