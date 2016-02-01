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
package org.powerapi.daemon

import java.util.concurrent.TimeUnit

import scala.collection.JavaConversions._
import scala.concurrent.duration.{FiniteDuration, DurationLong}

import com.typesafe.config.Config

import org.powerapi.core.{Configuration, ConfigValue}

/**
 * Main configuration.
 *
 * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
 */
trait DaemonConfiguration extends Configuration {
  /**
   * List of power-meter which the PowerAPI daemon has to load at his startup.
   * A power-meter is described by:
   * - a list of power modules
   * - a list of monitors described by:
   *   _ a list of pid
   *   _ a list of application
   *   _ a list of container
   *   _ a frequency
   *   _ an aggregator
   *   _ an output 
   */
  lazy val powerMeters: List[(Set[String], List[(Boolean, Set[String], Set[String], Set[String], FiniteDuration, String, String)])] = load { conf =>
    (for (powerMeter: Config <- conf.getConfigList("powerapi.daemon.load"))
    yield (
      powerMeter.getStringList("power-modules").toSet,
      (for (monitor: Config <- powerMeter.getConfigList("monitors"))
      yield (
        monitor.getBoolean("all"),
        monitor.getStringList("pids").toSet,
        monitor.getStringList("apps").toSet,
        monitor.getStringList("containers").toSet,
        monitor.getDuration("frequency", TimeUnit.MILLISECONDS).milliseconds,
        monitor.getString("agg"),
        monitor.getString("output")
      )).toList
    )).toList
  } match {
    case ConfigValue(values) => values
    case _ => List()
  }
}

