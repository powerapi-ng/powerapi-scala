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

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import org.powerapi.UnitTest
import scala.concurrent.duration.DurationInt

class LibpfmFormulaConfigurationSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("LibpfmFormulaConfigurationSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  trait Formulae {
    val formulae1 = Map[String, Double](
      "REQUESTS_TO_L2:CANCELLED" -> 8.002e-09,
      "REQUESTS_TO_L2:ALL" -> 1.251e-08,
      "LS_DISPATCH:STORES" -> 3.520e-09,
      "LS_DISPATCH:ALL" -> 6.695e-09,
      "LS_DISPATCH:LOADS" -> 9.504e-09
    )

    val formulae2 = Map[String, Double](
      "e1" -> 1e-8
    )
  }

  "The LibpfmFormulaConfiguration" should "read correctly the values from a resource file" in new Formulae {
    val configuration1 = new LibpfmFormulaConfiguration(None)
    val configuration2 = new LibpfmFormulaConfiguration(Some("libpfm"))

    configuration1.samplingInterval should equal(125.milliseconds)
    configuration1.formula should equal(formulae1)

    configuration2.samplingInterval should equal(10.milliseconds)
    configuration2.formula should equal(formulae2)
  }
}
