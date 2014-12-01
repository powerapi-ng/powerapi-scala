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
package org.powerapi.core

import com.typesafe.config.{Config, ConfigException, ConfigFactory}

/**
 * Base trait for configuration result.
 */
trait ConfigResult[T]

/**
 * Subtypes to specify the different types of result.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
case class ConfigValue[T](value: T) extends ConfigResult[T]
case class ConfigError[T](exception: Throwable) extends ConfigResult[T]

/**
 * Base trait for dealing with configuration files.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
trait Configuration {
  private lazy val conf = ConfigFactory.load()

  /**
   * Method to load a value from a configuration file.
   *
   * @param request: request for getting information.
   */
  protected def load[T](request: Config => T): ConfigResult[T] = {
    try {
      ConfigValue(request(conf))
    }
    catch {
      case ce: ConfigException => {
        ConfigError(ce)
      }
    } 
  }
}
