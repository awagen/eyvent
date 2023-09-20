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


package de.awagen.eyvent.io.json

import de.awagen.eyvent.config.NamingPatterns._
import spray.json.{DefaultJsonProtocol, JsObject, JsString, JsValue, JsonFormat, enrichAny}

object NamingPatternsJsonProtocol extends DefaultJsonProtocol {

  private val TYPE_KEY = "type"

  private val BY_YEAR_TYPE = "BY_YEAR"
  private val BY_MONTH_OF_YEAR_TYPE = "BY_MONTH_OF_YEAR"
  private val BY_DAY_OF_MONTH_TYPE = "BY_DAY_OF_MONTH"
  private val BY_HOUR_OF_DAY_TYPE = "BY_HOUR_OF_DAY"

  private val SEQUENTIAL_PARTITIONING_TYPE = "SEQUENTIAL"
  private val BY_YEAR_MONTH_DAY_HOUR_TYPE = "YEAR_MONTH_DAY_HOUR"
  private val PARTITIONS_KEY = "partitions"
  private val SEPARATOR_KEY = "separator"

  implicit object LongPartitionFormat extends JsonFormat[Partition[Long]] {
    override def read(json: JsValue): Partition[Long] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case BY_YEAR_TYPE => ByYearPartition
        case BY_MONTH_OF_YEAR_TYPE => ByMonthOfYearPartition
        case BY_DAY_OF_MONTH_TYPE => ByDayOfMonthPartition
        case BY_HOUR_OF_DAY_TYPE => ByHourOfDayPartition
      }
    }

    override def write(obj: Partition[Long]): JsValue = obj match {
      case ByYearPartition => JsObject(Map(TYPE_KEY -> JsString(BY_YEAR_TYPE)))
      case ByMonthOfYearPartition => JsObject(Map(TYPE_KEY -> JsString(BY_MONTH_OF_YEAR_TYPE)))
      case ByDayOfMonthPartition => JsObject(Map(TYPE_KEY -> JsString(BY_DAY_OF_MONTH_TYPE)))
      case ByHourOfDayPartition => JsObject(Map(TYPE_KEY -> JsString(BY_HOUR_OF_DAY_TYPE)))
    }
  }


  implicit object LongPartitioningFormat extends JsonFormat[Partitioning[Long]] {

    override def read(json: JsValue): Partitioning[Long] = json match {
      case spray.json.JsObject(fields) => fields(TYPE_KEY).convertTo[String] match {
        case SEQUENTIAL_PARTITIONING_TYPE =>
          val partitions = fields(PARTITIONS_KEY).convertTo[Seq[Partition[Long]]]
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

    override def write(obj: Partitioning[Long]): JsValue = obj match {
      case SequentialPartitioning(partitions, partitionSeparator) => JsObject(Map(
        TYPE_KEY -> JsString(SEQUENTIAL_PARTITIONING_TYPE),
        PARTITIONS_KEY -> partitions.toJson,
        SEPARATOR_KEY -> JsString(partitionSeparator)
      ))
    }
  }

}
