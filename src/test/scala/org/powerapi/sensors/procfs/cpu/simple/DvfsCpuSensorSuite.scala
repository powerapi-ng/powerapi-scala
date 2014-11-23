/**
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

 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */

package org.powerapi.sensors.procfs.cpu.simple

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.sensors.procfs.cpu.CpuSensorChannel.TimeInStates

import scala.concurrent.duration.DurationInt

/**
 * DvfsCpuSensorSuite
 *
 * @author abourdon
 * @author mcolmant
 */
class DvfsCpuSensorSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("DvfsCpuSensorSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "A TimeInStates case class" should "compute the difference with another one" in {
    val timesLeft = TimeInStates(Map(1 -> 10, 2 -> 20, 3 -> 30, 4 -> 15))
    val timesRight = TimeInStates(Map(1 -> 1, 2 -> 2, 3 -> 3, 100 -> 100))

    (timesLeft - timesRight) should equal(TimeInStates(Map(1 -> 9, 2 -> 18, 3 -> 27, 4 -> 15)))
  }
}
