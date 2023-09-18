package de.awagen.eyvent.collections

import de.awagen.eyvent.collections.Measures.MeasureType.MeasureType
import de.awagen.eyvent.collections.Measures.MeasureUnit.MeasureUnit


object Measures {

  /**
   * Calculates string memory usage in bytes. For overall size estimation allow addition of chars,
   * such as in case of adding new lines between single strings (default addChars = 2).
   */
  private[collections] def stringMemoryUsageInBytes(str: String, addChars: Int = 2): Int = {
    (8.0 * ((((str.length + addChars) * 2.0) + 45.0) / 8.0)).toInt
  }

  object MeasureType extends Enumeration {
    type MeasureType = Value

    val MEMORY_SIZE: Value = super.Value
    val TIME_IN_MILLIS: Value = super.Value
    val COUNT: Value = super.Value

  }

  object MeasureUnit extends Enumeration {
    type MeasureUnit = Value

    val MB: Value = super.Value
    val UNITS: Value = super.Value
    val MILLISECONDS: Value = super.Value
  }

  trait Measure[A, +B] {

    def measureType: MeasureType

    def unit: MeasureUnit

    def update(element: A): Unit

    def value: B

  }

  case class BaseMeasure[A, B](measureType: MeasureType,
                               unit: MeasureUnit,
                               var currentValue: B,
                               updateFunc: (A, B) => B) extends Measure[A, B] {
    override def update(element: A): Unit = {
      currentValue = updateFunc(element, currentValue)
    }

    override def value: B = currentValue
  }

  /**
   * The accumulated size of elements in megaBytes
   */
  class MemorySizeInMB[A](elementToSizeFunc: A => Double)
    extends BaseMeasure[A, Double](
      MeasureType.MEMORY_SIZE,
      MeasureUnit.MB,
      0.0D,
      (newEl, oldSize) => elementToSizeFunc(newEl) + oldSize
    )

  class StringMemorySizeInMB() extends MemorySizeInMB[String](x => stringMemoryUsageInBytes(x) / 1000000.0D)

  /**
   * Number of elements added so far
   */
  class NumElements[A]()
    extends BaseMeasure[A, Int](
      MeasureType.COUNT,
      MeasureUnit.UNITS,
      0,
      (_, oldSize) => oldSize + 1
    )

  /**
   * Measure that keeps track of the last time an update was made
   */
  class LastUpdateTimeInMillis[A]()
    extends BaseMeasure[A, Long](
      MeasureType.TIME_IN_MILLIS,
      MeasureUnit.MILLISECONDS,
      System.currentTimeMillis(),
      (_, _) => System.currentTimeMillis()
    )

  /**
   * Time of first addition of element in milliseconds
   */
  class FirstUpdateTimeInMillis[A]() extends BaseMeasure[A, Long](
    MeasureType.TIME_IN_MILLIS,
    MeasureUnit.MILLISECONDS,
    -1,
    (_, currentValue) => if (currentValue < 0) System.currentTimeMillis() else currentValue
  )


}