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
package org.powerapi.reporter

import java.util.UUID

import scala.concurrent.duration.DurationInt
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.Tick
import org.powerapi.core.power._
import org.powerapi.core.target.{Application, Process, Target}
import org.powerapi.module.PowerChannel.AggregatePowerReport

class JFreeChartDisplaySuite extends UnitTest {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.shutdown()
  }

  "A JFreeChartDisplay" should "display an AggPowerReport message in a JFreeChart" ignore {
    val muid = UUID.randomUUID()
    val baseTimestamp = System.currentTimeMillis()
    val baseTargets = Set[Target](Application("firefox"), Process(1), Process(2))
    val baseDevices = Set[String]("cpu", "gpu", "ssd")
    val basePower = 10.W

    val out = new JFreeChartDisplay

    for (i <- 0 to 5) {
      val baseTick = new Tick {
        val topic = ""
        val timestamp = baseTimestamp + i * 1000
      }
      val aggregatePowerReport = new AggregatePowerReport(muid) {
        override def ticks = Set(baseTick)
        override def targets = baseTargets
        override def devices = baseDevices
        override def power = basePower + (10 * i).W
      }
      out.display(aggregatePowerReport)
      Thread.sleep(1000)
    }
  }
}
