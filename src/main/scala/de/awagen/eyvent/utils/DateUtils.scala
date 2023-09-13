package de.awagen.eyvent.utils

import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import org.joda.time.{DateTime, DateTimeZone}

object DateUtils {

  val DATE_PATTERN = "yyyy-MM-dd HH:mm:ss"
  val DATE_NO_TIME_PATTERN = "yyyy-MM-dd"
  val DATE_TIME_ZONE: DateTimeZone = DateTimeZone.UTC
  val DATE_FORMATTER: DateTimeFormatter = DateTimeFormat
    .forPattern(DATE_PATTERN)
    .withZone(DATE_TIME_ZONE)

  def timeInMillisToDateTime(timeInMillis: Long): DateTime = {
    new DateTime(timeInMillis, DATE_TIME_ZONE)
  }

  def timeInMillisToFormattedDate(timeInMillis: Long): String = {
    new DateTime(timeInMillis, DATE_TIME_ZONE).toString(DATE_NO_TIME_PATTERN)
  }

  def timeInMillisToFormattedTime(timeInMillis: Long): String = {
    new DateTime(timeInMillis, DATE_TIME_ZONE).toString(DATE_FORMATTER)
  }

  def timeStringToTimeInMillis(timeStr: String): Long = {
    DateTime.parse(timeStr, DATE_FORMATTER).toInstant.getMillis
  }

}
