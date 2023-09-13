package de.awagen.eyvent.config

import com.amazonaws.regions.Regions
import com.typesafe.config.{Config, ConfigFactory}
import de.awagen.eyvent.config.EnvVariableKeys.{HTTP_SERVER_PORT, PROFILE}
import de.awagen.eyvent.config.NamingPatterns.Partitioning
import de.awagen.eyvent.io.json.NamingPatternsJsonProtocol.PartitioningFormat
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._
import scala.util.Random
import spray.json._


/**
  * Object where config values are stored for the app
  */
object AppProperties {

  private[this] val logger: Logger = LoggerFactory.getLogger(AppProperties.getClass)

  val profile: String = PROFILE.value
  val config: BaseConfig = BaseConfig(profile)

  def getFiniteDuration(config: Config, key: String, timeUnit: TimeUnit): FiniteDuration = {
    FiniteDuration(config.getInt(key), timeUnit)
  }

  def loadConfig(profile: String): Config = {
    val configName: String = if (profile.trim.isEmpty) "application.conf" else s"application-$profile.conf"
    logger.info(s"loading config: $configName")
    val loadedConfig: Config = ConfigFactory.load(configName)
    logger.debug(s"loaded config: ${loadedConfig.toString}")
    val configValues = new java.util.HashMap[String, Any]()
    ConfigFactory.parseMap(configValues).withFallback(loadedConfig)
  }


  case class BaseConfig private(profile: String) {

    val baseConfig: Config = loadConfig(profile)

    val http_server_port: Int = HTTP_SERVER_PORT.value.toInt

    def safeGetString(path: String): Option[String] = {
      if (baseConfig.hasPath(path)) Some(baseConfig.getString(path))
      else None
    }

    val awsS3Bucket: Option[String] = safeGetString("eyvent.persistence.s3.bucket")

    val awsS3BucketPath: Option[String] = safeGetString("eyvent.persistence.s3.bucketPath")

    val awsS3Region: Option[Regions] = safeGetString("eyvent.persistence.s3.region").map(x => Regions.valueOf(x))

    val gcpGSBucket: Option[String] = safeGetString("eyvent.persistence.gs.bucket")

    val gcpGSBucketPath: Option[String] = safeGetString("eyvent.persistence.gs.bucketPath")

    val gcpGSProjectID: Option[String] = safeGetString("eyvent.persistence.gs.projectID")

    val localPersistenceWriteBasePath: Option[String] = safeGetString("eyvent.persistence.local.writeBasePath")

    val localPersistenceReadBasePath: Option[String] = safeGetString("eyvent.persistence.local.readBasePath")

    val localResourceReadBasePath: Option[String] = safeGetString("eyvent.persistence.local.resources.readBasePath")

    val jobTemplatesPath: Option[String] = safeGetString("eyvent.persistence.templates.jobTemplatesPath")
    val outputResultsPath: Option[String] = safeGetString("eyvent.persistence.outputs.resultsPath")

    val persistenceMode: String = baseConfig.getString("eyvent.persistence.mode")
    val persistenceModuleClass: Option[String] = safeGetString("eyvent.persistence.moduleClass")

    val directoryPathSeparator: String = baseConfig.getString("eyvent.persistence.directoryPathSeparator")

    val appBlockingPoolThreads: Int = baseConfig.getInt("eyvent.blockingPoolThreads")
    val appNonBlockingPoolThreads: Int = baseConfig.getInt("eyvent.nonBlockingPoolThreads")
    val nettyHttpClientThreadsMax: Int = baseConfig.getInt("eyvent.netty.httpClientThreadsMax")
    val connectionPoolSizeMin: Int = baseConfig.getInt("eyvent.request.connectionPoolSizeMin")
    val connectionPoolSizeMax: Int = baseConfig.getInt("eyvent.request.connectionPoolSizeMax")
    val connectionTTLInSeconds: Int = baseConfig.getInt("eyvent.request.connectionTTLInSeconds")
    val connectionTimeoutInSeconds: Int = baseConfig.getInt("eyvent.request.connectionTimeoutInSeconds")
    val connectionPoolType: String = safeGetString("eyvent.request.connectionPoolType").map(typeStr => typeStr.toUpperCase).getOrElse("FIXED")


    // node-hash to identify and distinguish claims and processing states from
    // distinct nodes
    val node_hash: String = safeGetString("eyvent.nodeHash").getOrElse(
      Random.alphanumeric.take(6).mkString
    )
    // the partitioning for events
    val eventPartitioning: Partitioning =  baseConfig.getString("eyvent.events.partitioning").parseJson.convertTo[Partitioning]

    val validGroups: Seq[String] = baseConfig.getString("eyvent.events.validGroups")
      .split(",")
      .map(x => x.trim.toUpperCase)
      .filter(x => x.nonEmpty)
    val acceptAllGroups = validGroups.contains("*")

  }

}
