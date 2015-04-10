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

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import org.powerapi.UnitTest
import scala.concurrent.duration.DurationInt

class SamplingConfigurationSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("SamplingConfigurationSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
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
    configuration.baseFrequency should equal(0.133)
    configuration.maxFrequency should equal(2.66)
    configuration.topology should equal(Map(0 -> Set(0, 4), 1 -> Set(1, 5), 2 -> Set(2, 6), 3 -> Set(3, 7)))
    configuration.samplingDir should equal("test-samples")
    configuration.processingDir should equal("test-processing")
    configuration.computingDir should equal("test-computing")
  }
}
