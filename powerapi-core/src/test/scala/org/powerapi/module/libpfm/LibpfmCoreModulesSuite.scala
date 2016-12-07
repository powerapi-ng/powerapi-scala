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
import org.powerapi.module.libpfm.cycles.LibpfmCoreCyclesFormula
import org.scalamock.scalatest.MockFactory

class LibpfmCoreModulesSuite extends UnitTest with MockFactory {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.terminate()
  }

  "The LibpfmCoreModule class" should "create the underlying classes (sensor/formula)" in {
    val libpfmHelper = mock[LibpfmHelper]
    val module = new LibpfmCoreModule(libpfmHelper, 4.seconds, Map(10 -> Set(10)), BitSet(22), Set("e1"), "Threads", "Refs", Map(1d -> List(1d, 2d)), 10.milliseconds)

    module.sensor.get._1 should equal(classOf[LibpfmCoreSensor])
    module.sensor.get._2.size should equal(5)
    module.sensor.get._2(0) should equal(libpfmHelper)
    module.sensor.get._2(1) should equal(Timeout(4.seconds))
    module.sensor.get._2(2) should equal(Map(10 -> Set(10)))
    module.sensor.get._2(3) should equal(BitSet(22))
    module.sensor.get._2(4) should equal(Set("e1"))

    module.formula.get._1 should equal(classOf[LibpfmCoreCyclesFormula])
    module.formula.get._2.size should equal(4)
    module.formula.get._2(0) should equal("Threads")
    module.formula.get._2(1) should equal("Refs")
    module.formula.get._2(2) should equal(Map(1d -> List(1d, 2d)))
    module.formula.get._2(3) should equal(10.milliseconds)
  }

  "The LibpfmCoreModule object" should "build correctly the companion class" in {
    val libpfmHelper = mock[LibpfmHelper]
    val module1 = LibpfmCoreModule(libpfmHelper = libpfmHelper)
    val module2 = LibpfmCoreModule(Some("libpfm"), libpfmHelper)

    val formulae = Map[Double, List[Double]](
      12d -> List(85.7545270697, 1.10006565433e-08, -2.0341944068e-18),
      13d -> List(87.0324917754, 9.03486530986e-09, -1.31575869787e-18),
      14d -> List(86.3094440375, 1.04895773556e-08, -1.61982669617e-18),
      15d -> List(88.2194900717, 8.71468661777e-09, -1.12354133527e-18),
      16d -> List(85.8010062547, 1.05239105674e-08, -1.34813984791e-18),
      17d -> List(85.5127064474, 1.05732955159e-08, -1.28040830962e-18),
      18d -> List(85.5593567382, 1.07921513277e-08, -1.22419197787e-18),
      19d -> List(87.2004521609, 9.99728883739e-09, -9.9514346029e-19),
      20d -> List(87.7358230435, 1.00553994023e-08, -1.00002335486e-18),
      21d -> List(94.4635683042, 4.83140424765e-09, 4.25218895447e-20),
      22d -> List(104.356371072, 3.75414807806e-09, 6.73289818651e-20)
    )


    module1.sensor.get._1 should equal(classOf[LibpfmCoreSensor])
    module1.sensor.get._2.size should equal(5)
    module1.sensor.get._2(0) should equal(libpfmHelper)
    module1.sensor.get._2(1) should equal(Timeout(10.seconds))
    module1.sensor.get._2(2) should equal(Map(0 -> Set(0, 4), 1 -> Set(1, 5), 2 -> Set(2, 6), 3 -> Set(3, 7)))
    module1.sensor.get._2(3) should equal(BitSet(0, 1, 2, 10))
    module1.sensor.get._2(4) should equal(Set("CPU_CLK_UNHALTED:THREAD_P", "CPU_CLK_UNHALTED:REF_P"))

    module1.formula.get._1 should equal(classOf[LibpfmCoreCyclesFormula])
    module1.formula.get._2.size should equal(4)
    module1.formula.get._2(0) should equal("Test:cyclesThreadName")
    module1.formula.get._2(1) should equal("Test:cyclesRefName")
    module1.formula.get._2(2) should equal(formulae)
    module1.formula.get._2(3) should equal(125.milliseconds)


    module2.sensor.get._1 should equal(classOf[LibpfmCoreSensor])
    module2.sensor.get._2.size should equal(5)
    module2.sensor.get._2(0) should equal(libpfmHelper)
    module2.sensor.get._2(1) should equal(Timeout(10.seconds))
    module2.sensor.get._2(2) should equal(Map(0 -> Set(0, 4), 1 -> Set(1, 5), 2 -> Set(2, 6), 3 -> Set(3, 7)))
    module2.sensor.get._2(3) should equal(BitSet(11))
    module2.sensor.get._2(4) should equal(Set("event"))


    module2.formula.get._1 should equal(classOf[LibpfmCoreCyclesFormula])
    module2.formula.get._2.size should equal(4)
    module2.formula.get._2(0) should equal("Test:cyclesThreadName")
    module2.formula.get._2(1) should equal("Test:cyclesRefName")
    module2.formula.get._2(2) should equal(Map[Double, List[Double]](1d -> List(10.0, 1.0e-08, -4.0e-18)))
    module2.formula.get._2(3) should equal(10.milliseconds)
  }

  "The LibpfmCoreSensorModule object" should "build correctly the companion class" in {
    val libpfmHelper = mock[LibpfmHelper]
    val module1 = LibpfmCoreSensorModule(libpfmHelper = libpfmHelper)
    val module2 = LibpfmCoreSensorModule(Some("libpfm"), libpfmHelper, Set("e1"))
    val module3 = LibpfmCoreSensorModule(Some("libpfm"), libpfmHelper)

    module1.sensor.get._1 should equal(classOf[LibpfmCoreSensor])
    module1.sensor.get._2.size should equal(5)
    module1.sensor.get._2(0) should equal(libpfmHelper)
    module1.sensor.get._2(1) should equal(Timeout(10.seconds))
    module1.sensor.get._2(2) should equal(Map(0 -> Set(0, 4), 1 -> Set(1, 5), 2 -> Set(2, 6), 3 -> Set(3, 7)))
    module1.sensor.get._2(3) should equal(BitSet(0, 1, 2, 10))
    module1.sensor.get._2(4) should equal(Set("CPU_CLK_UNHALTED:THREAD_P", "CPU_CLK_UNHALTED:REF_P"))

    module1.formula should equal(None)

    module2.sensor.get._1 should equal(classOf[LibpfmCoreSensor])
    module2.sensor.get._2.size should equal(5)
    module2.sensor.get._2(0) should equal(libpfmHelper)
    module2.sensor.get._2(1) should equal(Timeout(10.seconds))
    module2.sensor.get._2(2) should equal(Map(0 -> Set(0, 4), 1 -> Set(1, 5), 2 -> Set(2, 6), 3 -> Set(3, 7)))
    module2.sensor.get._2(3) should equal(BitSet(11))
    module2.sensor.get._2(4) should equal(Set("e1"))

    module2.formula should equal(None)

    module3.sensor.get._1 should equal(classOf[LibpfmCoreSensor])
    module3.sensor.get._2.size should equal(5)
    module3.sensor.get._2(0) should equal(libpfmHelper)
    module3.sensor.get._2(1) should equal(Timeout(10.seconds))
    module3.sensor.get._2(2) should equal(Map(0 -> Set(0, 4), 1 -> Set(1, 5), 2 -> Set(2, 6), 3 -> Set(3, 7)))
    module3.sensor.get._2(3) should equal(BitSet(11))
    module3.sensor.get._2(4) should equal(Set("event"))

    module3.formula should equal(None)
  }
}
