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


package de.awagen.eyvent.config

import software.amazon.awssdk.regions.Region
import com.amazonaws.regions.Regions
import com.typesafe.config.{Config, ConfigFactory}
import de.awagen.eyvent.config.EnvVariableKeys.PROFILE
import de.awagen.eyvent.config.NamingPatterns.Partitioning
import de.awagen.eyvent.io.json.NamingPatternsJsonProtocol.LongPartitioningFormat
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

    val http_server_port: Int = baseConfig.getInt("eyvent.port")

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
    val eventPartitioning: Partitioning[Long] =  baseConfig.getString("eyvent.events.partitioning").parseJson.convertTo[Partitioning[Long]]

    val validGroups: Seq[String] = baseConfig.getString("eyvent.events.validGroups")
      .split(",")
      .map(x => x.trim.toUpperCase)
      .filter(x => x.nonEmpty)
    val acceptAllGroups = validGroups.contains("*")

    val structDefSubFolder: String = baseConfig.getString("eyvent.events.structDefSubFolder")
    val eventStorageSubFolder: String = baseConfig.getString("eyvent.events.eventStorageSubFolder")

    val eventFileMaxFileSizeInMB: Int = baseConfig.getInt("eyvent.events.stores.maxFileSizeInMB")
    val eventFileMaxNumberOfEvents: Int = baseConfig.getInt("eyvent.events.stores.maxNumberOfEvents")

    // mapping of endpoint ids to files providing the struct def defining the
    // format of accepted events.
    // Note that the struct def file with the given name is to be found within
    // basePath/structDefSubFolder
    val eventEndpointToStructDefMapping: Map[String, String] = baseConfig.getString("eyvent.sources.eventEndpointToStructDefMapping")
      .split(",").map(x => x.trim).grouped(2).map(x => (x.head, x.last)).toMap

    // determines how events are retrieved, e.g via HTTP endpoint ("HTTP") or sqs queue ("AWS_SQS")
    val consumeMode: ConsumeMode.Value = ConsumeMode.withName(baseConfig.getString("eyvent.sources.consumeMode"))

    val awsSQSRegion: Region = safeGetString("eyvent.sources.sqs.region").getOrElse("") match {
      case e if e.nonEmpty =>
        Region.of(e)
      case _ =>
        null
    }

    // max number of messages requested from queue at any given time
    val awsSQSMaxNumRequestMessages: Int = baseConfig.getInt("eyvent.sources.sqs.maxNumRequestMessages")
    // max wait time between requesting new messages from queue
    val awsSQSMaxWaitTimeSeconds: Int = baseConfig.getInt("eyvent.sources.sqs.maxWaitTimeSeconds")
    // queue to consume the events from
    val awsSQSQueueUrl: String = baseConfig.getString("eyvent.sources.sqs.queueUrl")
    // the key within queue messages determining which group (/ instances / customers) the message belongs to
    val awsSQSGroupKey: String = baseConfig.getString("eyvent.sources.sqs.groupKey")
  }

}
