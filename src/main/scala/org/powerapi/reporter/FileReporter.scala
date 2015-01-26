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

import scalax.file.Path

/**
 * FileReporter's configuration part.
 */
trait Configuration extends org.powerapi.core.Configuration {
  import org.powerapi.core.ConfigValue
  
  /**
   * The output file path, build from prefix given by user.
   * Temporary file as default.
   */
  lazy val filePath = load { _.getString("powerapi.reporter.file.prefix") + System.nanoTime() } match {
    case ConfigValue(path) => path
    case _ => Path.createTempFile(prefix = "powerapi.reporter-file", deleteOnExit = false).path
  }
}

/**
 * Listen to powerReport and display its content into a given file.
 *
 * @author Aurélien Bourdon <aurelien.bourdon@gmail.com>
 * @author Loïc Huertas <l.huertas.pro@gmail.com>
 */
class FileReporter extends ReporterComponent with Configuration {
  import scalax.io.Resource
  import org.powerapi.core.FileHelper.using
  import org.powerapi.module.PowerChannel.PowerReport
  
  lazy val output = {
    if (log.isInfoEnabled) log.info("using {} as output file", filePath)
      Resource.fromFile(filePath)
  }

  def report(aggPowerReport: PowerReport) {
    output.append(s"$aggPowerReport\n")
  }
}

