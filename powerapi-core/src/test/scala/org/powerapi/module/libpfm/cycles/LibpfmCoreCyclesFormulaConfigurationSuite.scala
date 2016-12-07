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
package org.powerapi.module.libpfm.cycles

import scala.concurrent.duration.DurationInt

import akka.util.Timeout

import org.powerapi.UnitTest

class LibpfmCoreCyclesFormulaConfigurationSuite extends UnitTest {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.terminate()
  }

  trait Formulae {
    val formulae1 = Map[Double, List[Double]](
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

    val formulae2 = Map[Double, List[Double]](
      1d -> List(10.0, 1.0e-08, -4.0e-18)
    )
  }

  "The LibpfmCoreCyclesFormulaConfiguration" should "read correctly the values from a resource file" in new Formulae {
    val configuration1 = new LibpfmCoreCyclesFormulaConfiguration(None)
    val configuration2 = new LibpfmCoreCyclesFormulaConfiguration(Some("libpfm"))

    configuration1.cyclesRefName should equal("Test:cyclesRefName")
    configuration1.cyclesThreadName should equal("Test:cyclesThreadName")
    configuration1.samplingInterval should equal(125.milliseconds)
    configuration1.formulae should equal(formulae1)

    configuration2.cyclesRefName should equal("Test:cyclesRefName")
    configuration2.cyclesThreadName should equal("Test:cyclesThreadName")
    configuration2.samplingInterval should equal(10.milliseconds)
    configuration2.formulae should equal(formulae2)
  }
}
