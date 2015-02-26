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
package org.powerapi.reporter

import org.powerapi.core.{ConfigValue, Configuration}
import scalax.file.Path

/**
 * Main configuration.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
trait FileDisplayConfiguration extends Configuration {
  /**
   * The output file path, build from prefix given by user.
   * Temporary file as default.
   */
  lazy val filePath = load { _.getString("powerapi.reporter.file.prefix") + System.nanoTime() } match {
    case ConfigValue(path) => path
    case _ => Path.createTempFile(prefix = "powerapi.reporter-file", deleteOnExit = false).path
  }
}
