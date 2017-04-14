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
package org.powerapi.module.rapl

import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.module.hwc.{LikwidHelper, RAPLDomain}
import org.scalamock.scalatest.MockFactory

import scala.concurrent.duration.DurationInt

class RAPLModulesSuite extends UnitTest with MockFactory {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.terminate()
  }

  "The RAPLModule class" should "create the underlying classes (sensor/formula)" in {
    val likwidHelper = mock[LikwidHelper]

    val module = new RAPLModule(likwidHelper, RAPLDomain.DRAM)

    module.sensor.get._1 should equal(classOf[RAPLSensor])
    module.sensor.get._2.size should equal(2)
    module.sensor.get._2(0) should equal(likwidHelper)
    module.sensor.get._2(1) should equal(RAPLDomain.DRAM)

    module.formula.get._1 should equal(classOf[RAPLFormula])
    module.formula.get._2.size should equal(1)
    module.formula.get._2(0) should equal(RAPLDomain.DRAM)
  }

  "The RaplCpuModule object" should "build correctly the companion class" in {
    val likwidHelper = mock[LikwidHelper]

    val module = RaplCpuModule(likwidHelper)

    module.sensor.get._1 should equal(classOf[RAPLSensor])
    module.sensor.get._2(0) should equal(likwidHelper)
    module.sensor.get._2(1) should equal(RAPLDomain.PKG)

    module.formula.get._1 should equal(classOf[RAPLFormula])
    module.formula.get._2.size should equal(1)
    module.formula.get._2(0) should equal(RAPLDomain.PKG)
  }

  "The RaplDramModule object" should "build correctly the companion class" in {
    val likwidHelper = mock[LikwidHelper]

    val module = RaplDramModule(likwidHelper)

    module.sensor.get._1 should equal(classOf[RAPLSensor])
    module.sensor.get._2(0) should equal(likwidHelper)
    module.sensor.get._2(1) should equal(RAPLDomain.DRAM)

    module.formula.get._1 should equal(classOf[RAPLFormula])
    module.formula.get._2.size should equal(1)
    module.formula.get._2(0) should equal(RAPLDomain.DRAM)
  }
}
