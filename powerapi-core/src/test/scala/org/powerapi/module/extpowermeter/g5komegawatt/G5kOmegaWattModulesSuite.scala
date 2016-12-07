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
import org.powerapi.core.power._
import org.powerapi.core.{ExternalPMeter, LinuxHelper, OSHelper}
import org.scalamock.scalatest.MockFactory

class G5kOmegaWattModulesSuite extends UnitTest with MockFactory {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.terminate()
  }

  "The G5kOmegaWattModule class" should "create the underlying classes (sensor/formula)" in {
    val osHelper = mock[OSHelper]
    val pMeter = mock[ExternalPMeter]

    val module = new G5kOmegaWattModule(osHelper, pMeter, 10.W)

    module.sensor.get._1 should equal(classOf[G5kOmegaWattSensor])
    module.sensor.get._2.size should equal(3)
    module.sensor.get._2(0) should equal(osHelper)
    module.sensor.get._2(1) should equal(pMeter)
    module.sensor.get._2(2) should equal(10.W)

    module.formula.get._1 should equal(classOf[G5kOmegaWattFormula])
    module.formula.get._2 should be('empty)
  }

  "The G5kOmegaWattModule object" should "build correctly the companion class" in {
    val module = G5kOmegaWattModule()

    module.sensor.get._1 should equal(classOf[G5kOmegaWattSensor])
    module.sensor.get._2.size should equal(3)
    module.sensor.get._2(0).getClass should equal(classOf[LinuxHelper])
    module.sensor.get._2(1).getClass should equal(classOf[G5kOmegawattPMeter])
    module.sensor.get._2(2) should equal(87.50.W)

    module.formula.get._1 should equal(classOf[G5kOmegaWattFormula])
    module.formula.get._2 should be('empty)
  }
}