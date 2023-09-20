package de.awagen.eyvent.collections

import de.awagen.eyvent.collections.Measures.Measure

object Conditions {


  type Condition[A] = Map[String, Measure[A, Any]] => Boolean


  def numericCondition[A, B](cond: B => Boolean, key: String)(implicit ev: Numeric[B]): Condition[A] = {
    map => map.get(key).exists(x => cond(x.value().asInstanceOf[B]))
  }

  def doubleGEQCondition[A](limit: Double, key: String): Condition[A] = {
    numericCondition[A, Double](x => x >= limit, key)
  }

  def intGEQCondition[A](limit: Int, key: String): Condition[A] = {
    numericCondition[A, Int](x => x >= limit, key)
  }

  def orCondition[A](seq: Seq[Condition[A]]): Condition[A] = {
    map => seq.map(cond => cond(map)).exists(identity)
  }

}
