package de.awagen.eyvent.config.di.modules.persistence

import com.softwaremill.macwire.wire
import de.awagen.eyvent.config.AppProperties
import de.awagen.eyvent.config.di.modules.Modules.PersistenceDIModule
import de.awagen.eyvent.config.di.modules.persistence.PersistenceModule.logger
import de.awagen.kolibri.datatypes.tagging.Tags
import de.awagen.kolibri.datatypes.types.SerializableCallable.SerializableFunction1
import org.slf4j.{Logger, LoggerFactory}

import scala.util.Random

object PersistenceModule {

  lazy private val logger: Logger = LoggerFactory.getLogger(this.getClass)

}

class PersistenceModule {

  lazy val persistenceDIModule: PersistenceDIModule = AppProperties.config.persistenceMode match {
    case "AWS" => wire[AwsPersistenceModule]
    case "GCP" => wire[GCPPersistenceModule]
    case "LOCAL" => wire[LocalPersistenceModule]
    case "RESOURCE" => wire[ResourcePersistenceModule]
    case "CLASS" =>
      val module: String = AppProperties.config.persistenceModuleClass.get
      logger.info(s"using classloader to load persistence module: $module")
      this.getClass.getClassLoader.loadClass(module).getDeclaredConstructor().newInstance().asInstanceOf[PersistenceDIModule]
    case _ => wire[LocalPersistenceModule]
  }

  lazy val keyToFilenameFunc: SerializableFunction1[Tags.Tag, String] = new SerializableFunction1[Tags.Tag, String] {
    val randomAdd: String = Random.alphanumeric.take(5).mkString
    override def apply(v1: Tags.Tag): String = s"${v1.toString}-$randomAdd"
  }


}
