package de.awagen.eyvent.io.json

import de.awagen.eyvent.config.NamingPatterns._
import spray.json.{DefaultJsonProtocol, JsObject, JsString, JsValue, JsonFormat, enrichAny}

object NamingPatternsJsonProtocol extends DefaultJsonProtocol {

  private val TYPE_KEY = "type"
  private val VALUE_KEY = "value"
  private val KEY_KEY = "key"

  private val BY_YEAR_TYPE = "BY_YEAR"
  private val BY_MONTH_OF_YEAR_TYPE = "BY_MONTH_OF_YEAR"
  private val BY_DAY_OF_MONTH_TYPE = "BY_DAY_OF_MONTH"
  private val BY_HOUR_OF_DAY_TYPE = "BY_HOUR_OF_DAY"
  private val CONSTANT_TYPE = "CONSTANT"
  private val BY_TOP_LEVEL_KEY_TYPE = "BY_TOP_LEVEL_KEY"

  private val SEQUENTIAL_PARTITIONING_TYPE = "SEQUENTIAL"
  private val BY_YEAR_MONTH_DAY_HOUR_TYPE = "YEAR_MONTH_DAY_HOUR"
  private val PARTITIONS_KEY = "partitions"
  private val SEPARATOR_KEY = "separator"

  implicit object PartitionFormat extends JsonFormat[Partition] {
    override def read(json: JsValue): Partition = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case BY_YEAR_TYPE => ByYearPartition
        case BY_MONTH_OF_YEAR_TYPE => ByMonthOfYearPartition
        case BY_DAY_OF_MONTH_TYPE => ByDayOfMonthPartition
        case BY_HOUR_OF_DAY_TYPE => ByHourOfDayPartition
        case CONSTANT_TYPE => ConstantPartition(fields(VALUE_KEY).convertTo[String])
        case BY_TOP_LEVEL_KEY_TYPE =>
          ByTopLevelKeyPartition(fields(KEY_KEY).convertTo[String])
      }
    }

    override def write(obj: Partition): JsValue = obj match {
      case ByYearPartition => JsObject(Map(TYPE_KEY -> JsString(BY_YEAR_TYPE)))
      case ByMonthOfYearPartition => JsObject(Map(TYPE_KEY -> JsString(BY_MONTH_OF_YEAR_TYPE)))
      case ByDayOfMonthPartition => JsObject(Map(TYPE_KEY -> JsString(BY_DAY_OF_MONTH_TYPE)))
      case ByHourOfDayPartition => JsObject(Map(TYPE_KEY -> JsString(BY_HOUR_OF_DAY_TYPE)))
      case ConstantPartition(const) => JsObject(Map(
        TYPE_KEY -> JsString(CONSTANT_TYPE),
        VALUE_KEY -> JsString(const)
      ))
      case ByTopLevelKeyPartition(key) => JsObject(Map(
        TYPE_KEY -> JsString(BY_TOP_LEVEL_KEY_TYPE),
        KEY_KEY -> JsString(key)
      ))
    }
  }


  implicit object PartitioningFormat extends JsonFormat[Partitioning] {

    override def read(json: JsValue): Partitioning = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case SEQUENTIAL_PARTITIONING_TYPE =>
          val partitions = fields(PARTITIONS_KEY).convertTo[Seq[Partition]]
          val separator = fields(SEPARATOR_KEY).convertTo[String]
          SequentialPartitioning(partitions, separator)
        case BY_YEAR_MONTH_DAY_HOUR_TYPE =>
          val separator = fields(SEPARATOR_KEY).convertTo[String]
          SequentialPartitioning(
            Seq(ByYearPartition, ByMonthOfYearPartition, ByDayOfMonthPartition, ByHourOfDayPartition),
            separator
          )
      }
    }

    override def write(obj: Partitioning): JsValue = obj match {
      case SequentialPartitioning(partitions, partitionSeparator) => JsObject(Map(
        TYPE_KEY -> JsString(SEQUENTIAL_PARTITIONING_TYPE),
        PARTITIONS_KEY -> partitions.toJson,
        SEPARATOR_KEY -> JsString(partitionSeparator)
      ))
    }
  }

}
