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
package org.powerapi.core.power

/**
  * Power units.
  *
  * @author Loïc Huertas <l.huertas.pro@gmail.com>
  * @author Maxime Colmant <maxime.colmant@gmail.com>
  * @author Romain Rouvoy <romain.rouvoy@univ-lille1.fr>
  */
object PowerConverter extends Enumeration {
  val MILLIWATTS = new PowerUnitVal("mW", "MilliWatts") {
    def toMilliWatts(p: Double): Double = p

    def toWatts(p: Double): Double = p / (C1 / C0)

    def toKiloWatts(p: Double): Double = p / (C2 / C0)

    def toMegaWatts(p: Double): Double = p / (C3 / C0)

    def convert(p: Double, u: PowerUnit): Double = u.toMilliWatts(p)
  }
  val WATTS = new PowerUnitVal("W", "Watts") {
    def toMilliWatts(p: Double): Double = ch(p, C1 / C0, MAX / (C1 / C0))

    def toWatts(p: Double): Double = p

    def toKiloWatts(p: Double): Double = p / (C2 / C1)

    def toMegaWatts(p: Double): Double = p / (C3 / C1)

    def convert(p: Double, u: PowerUnit): Double = u.toWatts(p)
  }
  val KILOWATTS = new PowerUnitVal("kW", "KiloWatts") {
    def toMilliWatts(p: Double): Double = ch(p, C2 / C0, MAX / (C2 / C0))

    def toWatts(p: Double): Double = ch(p, C2 / C1, MAX / (C2 / C1))

    def toKiloWatts(p: Double): Double = p

    def toMegaWatts(p: Double): Double = p / (C3 / C2)

    def convert(p: Double, u: PowerUnit): Double = u.toKiloWatts(p)
  }
  val MEGAWATTS = new PowerUnitVal("MW", "MegaWatts") {
    def toMilliWatts(p: Double): Double = ch(p, C3 / C0, MAX / (C3 / C0))

    def toWatts(p: Double): Double = ch(p, C3 / C1, MAX / (C3 / C1))

    def toKiloWatts(p: Double): Double = ch(p, C3 / C2, MAX / (C3 / C2))

    def toMegaWatts(p: Double): Double = p

    def convert(p: Double, u: PowerUnit): Double = u.toMegaWatts(p)
  }
  // Constants for conversion methods
  val C0 = 1.0
  val C1 = C0 * 1000.0
  val C2 = C1 * 1000.0
  val C3 = C2 * 1000.0
  val MAX = Double.MaxValue

  def apply(name: String): PowerUnit = try {
    withName(name).asInstanceOf[PowerUnitVal]
  } catch {
    case nsee: NoSuchElementException => WATTS
  }

  /**
    * Scale p by m, checking for overflow.
    */
  def ch(p: Double, m: Double, over: Double): Double = {
    if (p > over) Double.MaxValue
    if (p < 0.0) 0.0
    p * m
  }

  sealed abstract class PowerUnitVal(name: String, description: String) extends Val(name) {
    def toMilliWatts(p: Double): Double

    def toWatts(p: Double): Double

    def toKiloWatts(p: Double): Double

    def toMegaWatts(p: Double): Double

    def convert(p: Double, u: PowerUnit): Double

    override def toString: String = name
  }
}
