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
package org.powerapi.sampling

import java.util.concurrent.TimeUnit
import com.typesafe.config.Config
import org.joda.time.format.PeriodFormatterBuilder
import org.powerapi.core.{LinuxHelper, ConfigValue, Configuration}
import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.collection.JavaConversions._

/**
 * Main configuration.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class SamplingConfiguration extends Configuration {
  lazy val samplingInterval: FiniteDuration = load { _.getDuration("powerapi.sampling.interval", TimeUnit.NANOSECONDS) } match {
    case ConfigValue(value) => value.nanoseconds
    case _ => 1l.seconds
  }

  lazy val nbSamples: Int = load { _.getInt("powerapi.sampling.nb-samples") } match {
    case ConfigValue(value) => value
    case _ => 1
  }

  lazy val dvfs: Boolean = load { _.getBoolean("powerapi.sampling.dvfs") } match {
    case ConfigValue(value) => value
    case _ => false
  }

  lazy val turbo: Boolean = load { _.getBoolean("powerapi.sampling.turbo") } match {
    case ConfigValue(value) => value
    case _ => false
  }

  lazy val steps: List[Int] = load { _.getIntList("powerapi.sampling.steps") } match {
    case ConfigValue(values) => values.map(_.toInt).toList.sortWith(_>_)
    case _ => List(100, 25)
  }

  lazy val stepDuration: Int = load { _.getInt("powerapi.sampling.step-duration") } match {
    case ConfigValue(value) => value
    case _ => 2
  }

  lazy val topology: Map[Int, Set[Int]] = load { conf =>
    (for (item: Config <- conf.getConfigList("powerapi.cpu.topology"))
      yield (item.getInt("core"), item.getDoubleList("indexes").map(_.toInt).toSet)).toMap
  } match {
    case ConfigValue(values) => values
    case _ => Map()
  }

  lazy val events: Set[String] = load { _.getStringList("powerapi.sampling.events") } match {
    case ConfigValue(values) => values.toSet
    case _ => Set()
  }

  lazy val baseOutput = "output-"
  lazy val powers = "powers"
  lazy val outputPowers = s"$baseOutput${powers.toLowerCase().replace('_', '-').replace(':', '-')}.dat"
  lazy val separator = "="
  lazy val formatter = new PeriodFormatterBuilder().appendHours()
    .appendSuffix("H ")
    .appendMinutes()
    .appendSuffix("m ")
    .appendSeconds()
    .appendSuffix("s ")
    .appendMillis()
    .appendSuffix("ms ")
    .toFormatter
  lazy val osHelper = new LinuxHelper()
}
