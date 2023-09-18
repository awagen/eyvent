package de.awagen.eyvent.collections

import de.awagen.eyvent.collections.Measures.Measure

object Conditions {


  type Condition[A] = Map[String, Measure[A, Any]] => Boolean


  def numericCondition[A](cond: Double => Boolean, key: String): Condition[A] = {
    map => map.get(key).exists(x => cond(x.value.asInstanceOf[Double]))
  }

  def numericGEQCondition[A](limit: Double, key: String): Condition[A] = {
    numericCondition[A](x => x >= limit, key)
  }

  def orCondition[A](seq: Seq[Condition[A]]): Condition[A] = {
    map => seq.map(cond => cond(map)).exists(identity)
  }

}
