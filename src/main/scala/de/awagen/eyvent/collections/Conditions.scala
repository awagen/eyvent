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
