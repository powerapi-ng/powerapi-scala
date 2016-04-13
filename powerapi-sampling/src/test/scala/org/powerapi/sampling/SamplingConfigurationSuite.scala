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
package org.powerapi.sampling

import scala.concurrent.duration.DurationInt

import akka.util.Timeout

import org.powerapi.UnitTest

class SamplingConfigurationSuite extends UnitTest {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.shutdown()
  }

  "The SamplingConfiguration" should "read correctly the values from a resource file" in {
    val configuration = new SamplingConfiguration {}
    configuration.events should equal(Set("THREAD_P", "REF_P"))
    configuration.samplingInterval should equal(250.milliseconds)
    configuration.nbSamples should equal(3)
    configuration.dvfs should equal(true)
    configuration.turbo should equal(true)
    configuration.steps should equal(List(100, 75, 25))
    configuration.stepDuration should equal(3)
    configuration.topology should equal(Map(0 -> Set(0, 4), 1 -> Set(1, 5), 2 -> Set(2, 6), 3 -> Set(3, 7)))
  }

  "The PolynomCyclesConfiguration" should "read correctly the values from a resource file" in {
    val configuration = new PolynomCyclesConfiguration {}
    configuration.baseFrequency should equal(0.100)
    configuration.maxFrequency should equal(3.0)
    configuration.unhaltedCycles should equal("EVENT1")
    configuration.refCycles should equal("EVENT2")
  }
}
