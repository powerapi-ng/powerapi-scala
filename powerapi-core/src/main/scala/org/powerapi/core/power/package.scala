/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2015 Inria, University of Lille 1.
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

import org.saddle.Vec

package object power {
  type PowerUnit = org.powerapi.core.power.PowerUnitSystem.PowerUnitVal
  final val MILLIWATTS = org.powerapi.core.power.PowerUnitSystem.MILLIWATTS
  final val WATTS      = org.powerapi.core.power.PowerUnitSystem.WATTS
  final val KILOWATTS  = org.powerapi.core.power.PowerUnitSystem.KILOWATTS
  final val MEGAWATTS  = org.powerapi.core.power.PowerUnitSystem.MEGAWATTS
  
  implicit final class DoublePower(private val value: Double) extends AnyVal {
	  def mW = Power(value, MILLIWATTS)
	  def W  = Power(value, WATTS)
	  def kW = Power(value, KILOWATTS)
	  def MW = Power(value, MEGAWATTS)
  }

  implicit final class LongPower(private val value: Long) extends AnyVal {
	  def mW = Power(value.toDouble, MILLIWATTS)
	  def W  = Power(value.toDouble, WATTS)
	  def kW = Power(value.toDouble, KILOWATTS)
	  def MW = Power(value.toDouble, MEGAWATTS)
  }

  implicit final class IntPower(private val value: Int) extends AnyVal {
	  def mW = Power(value.toDouble, MILLIWATTS)
	  def W  = Power(value.toDouble, WATTS)
	  def kW = Power(value.toDouble, KILOWATTS)
	  def MW = Power(value.toDouble, MEGAWATTS)
  }
  
  def MAX(s: Seq[Power]): Power = Vec(s.map(_.toMilliWatts):_*).max match {
    case Some(max) => max.mW
    case None => 0.mW
  }
  def MIN(s: Seq[Power]): Power = Vec(s.map(_.toMilliWatts):_*).min match {
    case Some(min) => min.mW
    case None => 0.mW
  }
  def GEOMEAN(s: Seq[Power]): Power  = Vec(s.map(_.toMilliWatts):_*).geomean.mW
  def LOGSUM(s: Seq[Power]): Power   = Vec(s.map(_.toMilliWatts):_*).logsum.mW
  def MEAN(s: Seq[Power]): Power     = Vec(s.map(_.toMilliWatts):_*).mean.mW
  def MEDIAN(s: Seq[Power]): Power   = Vec(s.map(_.toMilliWatts):_*).median.mW
  def STDEV(s: Seq[Power]): Power    = Vec(s.map(_.toMilliWatts):_*).stdev.mW
  def SUM(s: Seq[Power]): Power      = Vec(s.map(_.toMilliWatts):_*).sum.mW
  def VARIANCE(s: Seq[Power]): Power = Vec(s.map(_.toMilliWatts):_*).variance.mW
}

