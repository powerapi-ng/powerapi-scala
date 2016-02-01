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
package org.powerapi.module.cpu.dvfs

import com.typesafe.config.Config
import org.powerapi.core.ConfigValue
import scala.collection.JavaConversions

/**
 * Main configuration.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
trait CpuFormulaConfiguration extends org.powerapi.module.cpu.simple.CpuFormulaConfiguration {
  /**
   * Map of frequencies and their associated voltages.
   */
  lazy val frequencies = load { conf =>
    (for (item <- JavaConversions.asScalaBuffer(conf.getConfigList("powerapi.cpu.frequencies")))
      yield (item.asInstanceOf[Config].getInt("value"), item.asInstanceOf[Config].getDouble("voltage"))).toMap
  } match {
    case ConfigValue(freqs) => freqs
    case _ => Map[Int, Double]()
  }
}
