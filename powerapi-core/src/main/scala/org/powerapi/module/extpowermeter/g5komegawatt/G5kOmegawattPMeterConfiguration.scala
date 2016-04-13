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
package org.powerapi.module.extpowermeter.g5komegawatt

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.{DurationLong, FiniteDuration}

import org.powerapi.core.{ConfigValue, Configuration}

/**
  * Main configuration.
  *
  * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
  */
class G5kOmegaWattPMeterConfiguration(prefix: Option[String]) extends Configuration(prefix) {
  lazy val probe: String = load {
    _.getString(s"${configurationPath}g5k.probe")
  } match {
    case ConfigValue(address) => address
    case _ => ""
  }

  lazy val interval: FiniteDuration = load {
    _.getDuration(s"${configurationPath}g5k.interval", TimeUnit.NANOSECONDS)
  } match {
    case ConfigValue(value) => value.nanoseconds
    case _ => 1l.seconds
  }
}
