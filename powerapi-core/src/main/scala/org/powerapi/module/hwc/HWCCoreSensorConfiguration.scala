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
package org.powerapi.module.hwc

import java.util.concurrent.TimeUnit

import akka.util.Timeout
import com.typesafe.config.Config
import org.powerapi.core.{ConfigValue, Configuration}

import scala.collection.BitSet
import scala.collection.JavaConverters._
import scala.concurrent.duration.DurationLong

/**
  * Main configuration.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class HWCCoreSensorConfiguration(prefix: Option[String]) extends Configuration(prefix) {

  lazy val events = load {
    _.getStringList(s"${configurationPath}powerapi.hwc.events").asScala
  } match {
    case ConfigValue(values) => values.map(_.toString).toList
    case _ => List[String]("CPU_CLK_UNHALTED_CORE:FIXC1", "CPU_CLK_UNHALTED_REF:FIXC2")
  }
}
