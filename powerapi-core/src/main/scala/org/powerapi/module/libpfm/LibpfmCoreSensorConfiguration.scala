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
package org.powerapi.module.libpfm

import java.util.concurrent.TimeUnit
import akka.util.Timeout
import com.typesafe.config.Config
import org.powerapi.core.{ConfigValue, Configuration}
import scala.collection.BitSet
import scala.collection.JavaConversions._
import scala.concurrent.duration.DurationLong

/**
 * Main configuration.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class LibpfmCoreSensorConfiguration(prefix: Option[String]) extends Configuration(prefix) {
  lazy val timeout: Timeout = load { _.getDuration("powerapi.actors.timeout", TimeUnit.MILLISECONDS) } match {
    case ConfigValue(value) => Timeout(value.milliseconds)
    case _ => Timeout(15l.seconds)
  }

  lazy val topology: Map[Int, Set[Int]] = load { conf =>
    (for (item: Config <- conf.getConfigList("powerapi.cpu.topology"))
      yield (item.getInt("core"), item.getDoubleList("indexes").map(_.toInt).toSet)).toMap
  } match {
    case ConfigValue(values) => values
    case _ => Map()
  }

  /**
   * List of enabled bits for the perf_event_open maccro.
   * The bits to configure are described in the structure perf_event_attr available below.
   *
   * @see http://manpages.ubuntu.com/manpages/trusty/en/man2/perf_event_open.2.html
   */
  lazy val configuration =
    BitSet(
      (load { _.getIntList(s"${configurationPath}powerapi.libpfm.configuration") } match {
        case ConfigValue(values) => values.map(_.toInt).toList
        case _ => List[Int]()
      }): _*
    )

  lazy val events = load { _.getStringList(s"${configurationPath}powerapi.libpfm.events") } match {
    case ConfigValue(values) => values.map(_.toString).toSet
    case _ => Set[String]()
  }
}
