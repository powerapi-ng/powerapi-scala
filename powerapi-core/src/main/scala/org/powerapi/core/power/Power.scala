/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2014 Inria, University of Lille 1.
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
package org.powerapi.core.power

import scala.concurrent.duration._
import org.apache.logging.log4j.LogManager
import RawPower._

object Power {
  def apply(value: Double, unit: PowerUnit): Power = new RawPower(value, unit)
  def apply(value: Double, unit: String): Power    = new RawPower(value, PowerUnitSystem(unit))

  def fromJoule(joule: Double, duration: FiniteDuration = 1.second) = new RawPower(joule / (duration.toMillis / 1000.0), WATTS)

  /**
   * The natural ordering of powers matches the natural ordering for Double.
   */
  implicit object PowerIsOrdered extends Ordering[Power] {
    def compare(a: Power, b: Power) = a compare b
  }
}

trait Power extends Ordered[Power] {
  def value: Double
  def unit: PowerUnit
  def toMilliWatts: Double
  def toWatts: Double
  def toKiloWatts: Double
  def toMegaWatts: Double
  def toUnit(unit: PowerUnit): Double
  def +(other: Power): Power
  def -(other: Power): Power
  def *(factor: Double): Power
  def /(divisor: Double): Power
  def min(other: Power): Power = if (this < other) this else other
  def max(other: Power): Power = if (this > other) this else other
  
  // Java API
  def div(divisor: Double) = this / divisor
  def gt(other: Power)     = this > other
  def gteq(other: Power)   = this >= other
  def lt(other: Power)     = this < other
  def lteq(other: Power)   = this <= other
  def minus(other: Power)  = this - other
  def mul(factor: Double)  = this * factor
  def plus(other: Power)   = this + other
}

object RawPower {

  implicit object RawPowerIsOrdered extends Ordering[RawPower] {
    def compare(a: RawPower, b: RawPower) = a compare b
  }

  def apply(value: Double, unit: PowerUnit) = new RawPower(value, unit)
  def apply(value: Double, unit: String)    = new RawPower(value, PowerUnitSystem(unit))

  // limit on abs. value of powers in their units
  private final val max_mw = Double.MaxValue
  private final val max_w = max_mw / 1000.0
  private final val max_kw = max_w / 1000.0
  private final val max_Mw = max_kw / 1000.0
}

/**
 * Defines a power value.
 *
 * @author Loïc Huertas <l.huertas.pro@gmail.com>
 * @author Romain Rouvoy <romain.rouvoy@univ-lille1.fr>
 */
final class RawPower(val value: Double, val unit: PowerUnit) extends Power {
  private val log = LogManager.getLogger
  
  private[this] def bounded(max: Double) = 0.0 <= value && value <= max
  
  require(unit match {
      case MILLIWATTS => bounded(max_mw)
      case WATTS      => bounded(max_w)
      case KILOWATTS  => bounded(max_kw)
      case MEGAWATTS  => bounded(max_Mw)
      case _ =>
        val v = MEGAWATTS.convert(value, unit)
        0.0 <= v && v <= max_Mw
    }, "Power value is limited to 1.79e308 mW and cannot be negative")

  def toMilliWatts = unit.toMilliWatts(value)
  def toWatts      = unit.toWatts(value)
  def toKiloWatts  = unit.toKiloWatts(value)
  def toMegaWatts  = unit.toMegaWatts(value)
  def toUnit(u: PowerUnit) = toMilliWatts / MILLIWATTS.convert(1, u)
  
  override def toString() = s"$value $unit"
  
  def compare(other: Power) = toMilliWatts compare other.toMilliWatts
  
  private[this] def safeAdd(a: Double, b: Double): Double = {
    if ((b > 0.0) && (a > Double.MaxValue - b)) throw new IllegalArgumentException("double overflow")
    if ((b < 0.0) && (a < -b)) throw new IllegalArgumentException("negative power cannot exists")
    a + b
  }
  private[this] def add(otherValue: Double, otherUnit: PowerUnit): Power = {
    val commonUnit = if (otherUnit.convert(1, unit) < 1.0) unit else otherUnit
    val resultValue = safeAdd(commonUnit.convert(value, unit), commonUnit.convert(otherValue, otherUnit))
    new RawPower(resultValue, commonUnit)
  }

  def +(other: Power) = add(other.value, other.unit)
  def -(other: Power) = add(-other.value, other.unit)
  
  private[this] def safeMul(a: Double): Double = {
    if (a.isInfinite) throw new IllegalArgumentException("multiplication's result is an infinite value")
    if (a.isNaN) throw new IllegalArgumentException("multiplication's result is an undefined value")
    if (a > Double.MaxValue) throw new IllegalArgumentException("double overflow")
    if (a < 0.0) throw new IllegalArgumentException("negative power cannot exists")
    a
  }
  
  def *(factor: Double) = new RawPower({
      if (factor.isInfinite || factor.isNaN) throw new IllegalArgumentException("factor must be a finite and defined value")
      else safeMul(value * factor)
    }, unit
  )
  def /(divisor: Double) = new RawPower({
      if (divisor.isInfinite || divisor.isNaN) throw new IllegalArgumentException("divisor must be a finite and defined value")
      else safeMul(value / divisor)
    }, unit
  )
  
  override def equals(other: Any) = other match {
    case x: RawPower => toMilliWatts == x.toMilliWatts
    case _           => super.equals(other)
  }
}

