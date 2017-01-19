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

import scala.concurrent.duration.DurationInt

import akka.util.Timeout

import org.powerapi.UnitTest

class G5kOmegaWattPMeterConfigurationSuite extends UnitTest {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.terminate()
  }

  "The G5kOmegawattPMeterConfiguration" should "read correctly the values from a resource file" in {
    val configuration = new G5kOmegaWattPMeterConfiguration(None)

    configuration.probe should equal("http://kwapi.lyon.grid5000.fr:5000/probes/lyon.taurus-1/power/")
    configuration.interval should equal(250.milliseconds)
  }
}
