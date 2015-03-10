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
package org.powerapi.sampling

import org.powerapi.module.libpfm.LibpfmCoreSensorModule
import org.powerapi.module.powerspy.PowerSpyModule
import org.powerapi.PowerMeter
import scala.sys

/**
 * Main application.
 * This application has to be used with the bash script generated, not in console.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
object Application extends App with SamplingConfiguration {
  @volatile var powerapi: Option[PowerMeter] = None
  @volatile var externalPMeter: Option[PowerMeter] = None

  val shutdownHookThread = scala.sys.ShutdownHookThread {
    println("It's the time for sleeping! ...")

    powerapi match {
      case Some(papi) => {
        papi.shutdown()
      }
      case _ => {}
    }

    externalPMeter match {
      case Some(ePMeter) => {
        ePMeter.shutdown()
      }
      case _ => {}
    }

    org.powerapi.module.libpfm.LibpfmHelper.deinit()
    powerapi = None
    externalPMeter = None
  }

  org.powerapi.module.libpfm.LibpfmHelper.init()
  powerapi = Some(PowerMeter.loadModule(LibpfmCoreSensorModule()))
  externalPMeter = Some(PowerMeter.loadModule(PowerSpyModule()))

  Sampling(powerapi.get, externalPMeter.get).run()
  Processing().run()
  PolynomialRegression().run()

  shutdownHookThread.start()
  shutdownHookThread.join()
  shutdownHookThread.remove()
  sys.exit(0)
}
