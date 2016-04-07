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
package org.powerapi.module.extpowermeter.rapl

import scala.concurrent.duration.DurationInt

import akka.util.Timeout

import org.powerapi.UnitTest
import org.powerapi.core.{ExternalPMeter, LinuxHelper, OSHelper}
import org.scalamock.scalatest.MockFactory

class RAPLModulesSuite extends UnitTest with MockFactory {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.shutdown()
  }

  "The RAPLModule class" should "create the underlying classes (sensor/formula)" in {
    val osHelper = mock[OSHelper]
    val pMeter = mock[ExternalPMeter]

    val module = new RAPLModule(osHelper, pMeter)

    module.sensor.get._1 should equal(classOf[RAPLSensor])
    module.sensor.get._2.size should equal(2)
    module.sensor.get._2(0) should equal(osHelper)
    module.sensor.get._2(1) should equal(pMeter)

    module.formula.get._1 should equal(classOf[RAPLFormula])
    module.formula.get._2 should be('empty)
  }

  "The RAPLModule object" should "build correctly the companion class" in {
    val module = RAPLModule()

    module.sensor.get._1 should equal(classOf[RAPLSensor])
    module.sensor.get._2.size should equal(2)
    module.sensor.get._2(0).getClass should equal(classOf[LinuxHelper])
    module.sensor.get._2(1).getClass should equal(classOf[RAPLPMeter])

    module.formula.get._1 should equal(classOf[RAPLFormula])
    module.formula.get._2 should be('empty)
  }
}