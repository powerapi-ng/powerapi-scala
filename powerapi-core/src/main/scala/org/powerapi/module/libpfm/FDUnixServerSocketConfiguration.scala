/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2015 Inria, University of Lille 1.
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

import org.powerapi.core.{ConfigValue, Configuration}

/**
 * Main configuration.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class FDUnixServerSocketConfiguration(prefix: Option[String]) extends Configuration(prefix) {
  lazy val controlFlowServerPath = load { _.getString(s"${configurationPath}powerapi.libpfm.control-flow-server-path") } match {
    case ConfigValue(value) => value
    case _ => "/tmp/libpfm-control-flow-server-path.sock"
  }

  lazy val fdFlowServerPath = load { _.getString(s"${configurationPath}powerapi.libpfm.fd-flow-server-path") } match {
    case ConfigValue(value) => value
    case _ => "/tmp/libpfm-fd-flow-server-path.sock"
  }
}
