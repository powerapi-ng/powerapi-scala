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
package org.powerapi

import org.powerapi.core.PowerUnit

object PowerUnit extends Enumeration {

  case class PowerUnit(name: String, description: String) extends Val

  val mW = PowerUnit("mW", "MilliWatts")
  val W = PowerUnit("W", "Watts")
  val kW = PowerUnit("kW", "KiloWatts")
  val MW = PowerUnit("MW", "MegaWatts")
}

trait Power {
	def value: Double
	def unit: PowerUnit
}

/**
 * Defines a power value (to be completed).
 */
final class RawPower(val value: Double, val unit: PowerUnit) extends Power {
    def toMilliWatts = unit.toMilliWatts(value)
    def toWatts = unit.toWatts(value)
    def toKiloWatts = unit.toKiloWatts(value)
    def toMegaWatts = unit.toMegaWatts(value)

    //...
}

implicit class DoublePower(private val value: Double) extends AnyVal {
	def mW = RawPower(value,PowerUnit.mW)
	def W = RawPower(value,PowerUnit.W)
	def kW = RawPower(value,PowerUnit.kW)
	def MW = RawPower(value,PowerUnit.MW)
}

implicit class LongPower(private val value: Long) extends AnyVal {
	def mW = RawPower(value.toDouble,PowerUnit.mW)
	def W = RawPower(value.toDouble,PowerUnit.W)
	def kW = RawPower(value.toDouble,PowerUnit.kW)
	def MW = RawPower(value.toDouble,PowerUnit.MW)
}

implicit class IntPower(private val value: Int) extends AnyVal {
	def mW = RawPower(value.toDouble,PowerUnit.mW)
	def W = RawPower(value.toDouble,PowerUnit.W)
	def kW = RawPower(value.toDouble,PowerUnit.kW)
	def MW = RawPower(value.toDouble,PowerUnit.MW)
}
