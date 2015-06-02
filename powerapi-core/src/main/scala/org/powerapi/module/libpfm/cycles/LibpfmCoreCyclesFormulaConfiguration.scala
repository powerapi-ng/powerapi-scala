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
package org.powerapi.module.libpfm.cycles

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import org.powerapi.core.{Configuration, ConfigValue}
import scala.collection.JavaConversions._
import scala.concurrent.duration.DurationLong
import scala.concurrent.duration.FiniteDuration

/**
 * Main configuration.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class LibpfmCoreCyclesFormulaConfiguration(prefix: Option[String]) extends Configuration(prefix) {
  lazy val cyclesThreadName: String = load { _.getString("powerapi.libpfm.formulae.cycles-thread") } match {
    case ConfigValue(value) => value
    case _ => "CPU_CLK_UNHALTED:THREAD_P"
  }

  lazy val cyclesRefName: String = load { _.getString("powerapi.libpfm.formulae.cycles-ref") } match {
    case ConfigValue(value) => value
    case _ => "CPU_CLK_UNHALTED:REF_P"
  }

  lazy val formulae: Map[Double, List[Double]] = load { conf =>
    (for (item: Config <- conf.getConfigList(s"${configurationPath}powerapi.libpfm.formulae.cycles"))
    yield (item.getDouble("coefficient"), item.getDoubleList("formula").map(_.toDouble).toList)).toMap
  } match {
    case ConfigValue(values) => values
    case _ => Map()
  }

  lazy val samplingInterval: FiniteDuration = load { _.getDuration(s"${configurationPath}powerapi.sampling.interval", TimeUnit.NANOSECONDS) } match {
    case ConfigValue(value) => value.nanoseconds
    case _ => 1l.seconds
  }
}
