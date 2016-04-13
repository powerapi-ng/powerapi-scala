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
package org.powerapi.module.libpfm

import scala.collection.BitSet
import scala.concurrent.duration.DurationInt

import akka.util.Timeout

import org.powerapi.UnitTest
import org.powerapi.core.{LinuxHelper, OSHelper}
import org.scalamock.scalatest.MockFactory

class LibpfmProcessModulesSuite extends UnitTest with MockFactory {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.shutdown()
  }

  "The LibpfmProcessModule class" should "create the underlying classes (sensor/formula)" in {
    val osHelper = mock[OSHelper]
    val libpfmHelper = mock[LibpfmHelper]
    val module = new LibpfmProcessModule(osHelper, libpfmHelper, 4.seconds, Map(10 -> Set(10)), BitSet(22), Set("e1"), true, Map("e1" -> 2d), 10.milliseconds)

    module.sensor.get._1 should equal(classOf[LibpfmCoreProcessSensor])
    module.sensor.get._2.size should equal(7)
    module.sensor.get._2(0) should equal(osHelper)
    module.sensor.get._2(1) should equal(libpfmHelper)
    module.sensor.get._2(2) should equal(Timeout(4.seconds))
    module.sensor.get._2(3) should equal(Map(10 -> Set(10)))
    module.sensor.get._2(4) should equal(BitSet(22))
    module.sensor.get._2(5) should equal(Set("e1"))
    module.sensor.get._2(6) should equal(true)

    module.formula.get._1 should equal(classOf[LibpfmFormula])
    module.formula.get._2.size should equal(2)
    module.formula.get._2(0) should equal(Map("e1" -> 2d))
    module.formula.get._2(1) should equal(10.milliseconds)
  }

  "The LibpfmProcessModule object" should "build correctly the companion class" in {
    val libpfmHelper = mock[LibpfmHelper]
    val module1 = LibpfmProcessModule(libpfmHelper = libpfmHelper)
    val module2 = LibpfmProcessModule(Some("libpfm"), libpfmHelper)

    val formula = Map[String, Double](
      "REQUESTS_TO_L2:CANCELLED" -> 8.002e-09,
      "REQUESTS_TO_L2:ALL" -> 1.251e-08,
      "LS_DISPATCH:STORES" -> 3.520e-09,
      "LS_DISPATCH:ALL" -> 6.695e-09,
      "LS_DISPATCH:LOADS" -> 9.504e-09
    )

    module1.sensor.get._1 should equal(classOf[LibpfmCoreProcessSensor])
    module1.sensor.get._2.size should equal(7)
    module2.sensor.get._2(0).getClass should equal(classOf[LinuxHelper])
    module1.sensor.get._2(1) should equal(libpfmHelper)
    module1.sensor.get._2(2) should equal(Timeout(10.seconds))
    module1.sensor.get._2(3) should equal(Map(0 -> Set(0, 4), 1 -> Set(1, 5), 2 -> Set(2, 6), 3 -> Set(3, 7)))
    module1.sensor.get._2(4) should equal(BitSet(0, 1, 2, 10))
    module1.sensor.get._2(5) should equal(Set("CPU_CLK_UNHALTED:THREAD_P", "CPU_CLK_UNHALTED:REF_P"))
    module1.sensor.get._2(6) should equal(true)

    module1.formula.get._1 should equal(classOf[LibpfmFormula])
    module1.formula.get._2.size should equal(2)
    module1.formula.get._2(0) should equal(formula)
    module1.formula.get._2(1) should equal(125.milliseconds)

    module2.sensor.get._1 should equal(classOf[LibpfmCoreProcessSensor])
    module2.sensor.get._2.size should equal(7)
    module2.sensor.get._2(0).getClass should equal(classOf[LinuxHelper])
    module2.sensor.get._2(1) should equal(libpfmHelper)
    module2.sensor.get._2(2) should equal(Timeout(10.seconds))
    module2.sensor.get._2(3) should equal(Map(0 -> Set(0, 4), 1 -> Set(1, 5), 2 -> Set(2, 6), 3 -> Set(3, 7)))
    module2.sensor.get._2(4) should equal(BitSet(11))
    module2.sensor.get._2(5) should equal(Set("event"))
    module2.sensor.get._2(6) should equal(false)

    module2.formula.get._1 should equal(classOf[LibpfmFormula])
    module2.formula.get._2.size should equal(2)
    module2.formula.get._2(0) should equal(Map[String, Double]("e1" -> 1.0e-08))
    module2.formula.get._2(1) should equal(10.milliseconds)
  }
}
