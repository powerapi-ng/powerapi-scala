/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2016 Inria, University of Lille 1.
 *
 * PowerAPI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * PowerAPI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with PowerAPI.
 *
 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */
package org.powerapi.core

import Numeric._
import grizzled.math.stats._

package object power {
  type PowerUnit = org.powerapi.core.power.PowerConverter.PowerUnitVal
  final val MILLIWATTS = org.powerapi.core.power.PowerConverter.MILLIWATTS
  final val WATTS = org.powerapi.core.power.PowerConverter.WATTS
  final val KILOWATTS = org.powerapi.core.power.PowerConverter.KILOWATTS
  final val MEGAWATTS = org.powerapi.core.power.PowerConverter.MEGAWATTS

  def MAX(s: Seq[Power]): Power = s.map(_.toMilliWatts).max.mW

  def MIN(s: Seq[Power]): Power = s.map(_.toMilliWatts).min.mW

  def SUM(s : Seq[Power]): Power = s.map(_.toMilliWatts).sum.mW

  def MEAN(s: Seq[Power]): Power = mean(s.map(_.toMilliWatts): _*).mW

  def MEDIAN(s: Seq[Power]): Power = median(s.map(_.toMilliWatts): _*).mW

  implicit final class DoublePower(private val value: Double) extends AnyVal {
    def mW: Power = Power(value, MILLIWATTS)

    def W: Power = Power(value, WATTS)

    def kW: Power = Power(value, KILOWATTS)

    def MW: Power = Power(value, MEGAWATTS)
  }

  implicit final class LongPower(private val value: Long) extends AnyVal {
    def mW: Power = Power(value.toDouble, MILLIWATTS)

    def W: Power = Power(value.toDouble, WATTS)

    def kW: Power = Power(value.toDouble, KILOWATTS)

    def MW: Power = Power(value.toDouble, MEGAWATTS)
  }

  implicit final class IntPower(private val value: Int) extends AnyVal {
    def mW: Power = Power(value.toDouble, MILLIWATTS)

    def W: Power = Power(value.toDouble, WATTS)

    def kW: Power = Power(value.toDouble, KILOWATTS)

    def MW: Power = Power(value.toDouble, MEGAWATTS)
  }

}

