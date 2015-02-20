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
package org.powerapi.configuration

import org.powerapi.core.Configuration

/**
 * Main configuration for LibpfmCore sensors.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
trait LibpfmCoreConfiguration  {
  self: Configuration =>

  import org.powerapi.core.ConfigValue
  import scala.collection.BitSet
  import scala.collection.JavaConversions._

  /**
   * List of enabled bits for the perf_event_open maccro.
   * The bits to configure are described in the structure perf_event_attr available below.
   *
   * @see http://manpages.ubuntu.com/manpages/trusty/en/man2/perf_event_open.2.html
   */
  lazy val configuration =
    BitSet(
      (load { _.getIntList("powerapi.libpfm.configuration") } match {
        case ConfigValue(values) => values.map(_.toInt).toList
        case _ => List[Int]()
      }): _*
    )

  lazy val events = load { _.getStringList("powerapi.libpfm.events") } match {
    case ConfigValue(values) => values.map(_.toString).toList
    case _ => List[String]()
  }
}
