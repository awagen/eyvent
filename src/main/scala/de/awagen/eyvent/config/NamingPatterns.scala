package de.awagen.eyvent.config

import de.awagen.eyvent.utils.DateUtils
import spray.json.JsObject

object NamingPatterns {

  trait Partition {

    def partition: JsObject => String

  }

  object ByYearPartition extends Partition {
    override def partition: JsObject => String = _ => {
      DateUtils.timeInMillisToDateTime(System.currentTimeMillis()).getYear.toString
    }
  }

  object ByMonthOfYearPartition extends Partition {
    override def partition: JsObject => String = _ => {
      "%02d".format(DateUtils.timeInMillisToDateTime(System.currentTimeMillis()).getMonthOfYear)
    }
  }

  object ByDayOfMonthPartition extends Partition {
    override def partition: JsObject => String = _ => {
      "%02d".format(DateUtils.timeInMillisToDateTime(System.currentTimeMillis()).getDayOfMonth)
    }
  }

  object ByHourOfDayPartition extends Partition {
    override def partition: JsObject => String = _ => {
      "%02d".format(DateUtils.timeInMillisToDateTime(System.currentTimeMillis()).getHourOfDay)
    }
  }

  case class ConstantPartition(value: String) extends Partition {
    override def partition: JsObject => String = _ => value
  }

  case class ByTopLevelKeyPartition(key: String) extends Partition {
    override def partition: JsObject => String = obj => obj.fields(key).toString()
  }



  trait Partitioning {

    def partitionSeparator: String

    def partitions: Seq[Partition]

    def partition: JsObject => String

  }

  case class SequentialPartitioning(partitions: Seq[Partition], partitionSeparator: String) extends Partitioning {
    override def partition: JsObject => String = obj => {
      partitions.map(part => part.partition(obj)).mkString(partitionSeparator)
    }
  }

}
