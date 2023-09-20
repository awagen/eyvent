package de.awagen.eyvent.config

import de.awagen.eyvent.utils.DateUtils
import spray.json.JsObject

object NamingPatterns {

  case class PartitionDef(partitionId: String, group: String, timeCreatedInMillis: Long)

  trait Partition[T] {

    def partition: T => String

  }

  object ByYearPartition extends Partition[Long] {
    override def partition: Long => String = timeInMillis => {
      DateUtils.timeInMillisToDateTime(timeInMillis).getYear.toString
    }
  }

  object ByMonthOfYearPartition extends Partition[Long] {
    override def partition: Long => String = timeInMillis => {
      "%02d".format(DateUtils.timeInMillisToDateTime(timeInMillis).getMonthOfYear)
    }
  }

  object ByDayOfMonthPartition extends Partition[Long] {
    override def partition: Long => String = timeInMillis => {
      "%02d".format(DateUtils.timeInMillisToDateTime(timeInMillis).getDayOfMonth)
    }
  }

  object ByHourOfDayPartition extends Partition[Long] {
    override def partition: Long => String = timeInMillis => {
      "%02d".format(DateUtils.timeInMillisToDateTime(timeInMillis).getHourOfDay)
    }
  }

  case class ConstantPartition(value: String) extends Partition[Any] {
    override def partition: Any => String = _ => value
  }

  case class ByTopLevelKeyPartition(key: String) extends Partition[JsObject] {
    override def partition: JsObject => String = obj => obj.fields(key).toString()
  }



  trait Partitioning[T] {

    def partitionSeparator: String

    def partitions: Seq[Partition[T]]

    def partition: T => String

  }

  case class SequentialPartitioning[T](partitions: Seq[Partition[T]], partitionSeparator: String) extends Partitioning[T] {
    override def partition: T => String = obj => {
      partitions.map(part => part.partition(obj)).mkString(partitionSeparator)
    }
  }

}
