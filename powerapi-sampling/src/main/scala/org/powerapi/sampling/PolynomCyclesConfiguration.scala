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
package org.powerapi.sampling

import org.powerapi.core.ConfigValue

/**
 * Configuration for the cycle formulae.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class PolynomCyclesConfiguration extends SamplingConfiguration {
  lazy val baseFrequency: Double = load { _.getDouble("powerapi.cycles-polynom-regression.cpu-base-frequency") } match {
    case ConfigValue(value) => value
    case _ => 0d
  }

  lazy val maxFrequency: Double = load { _.getDouble("powerapi.cycles-polynom-regression.cpu-max-frequency") } match {
    case ConfigValue(value) => value
    case _ => 0d
  }

  lazy val unhaltedCycles = load { _.getString("powerapi.cycles-polynom-regression.unhalted-cycles-event") } match {
    case ConfigValue(value) => value
    case _ => "CPU_CLK_UNHALTED:THREAD_P"
  }

  lazy val refCycles = load { _.getString("powerapi.cycles-polynom-regression.ref-cycles-event") } match {
    case ConfigValue(value) => value
    case _ => "CPU_CLK_UNHALTED:REF_P"
  }

  override lazy val events = Set(unhaltedCycles, refCycles)
  lazy val outputUnhaltedCycles = s"$baseOutput${unhaltedCycles.toLowerCase().replace('_', '-').replace(':', '-')}.dat"
  lazy val outputRefCycles = s"$baseOutput${refCycles.toLowerCase().replace('_', '-').replace(':', '-')}.dat"
}
