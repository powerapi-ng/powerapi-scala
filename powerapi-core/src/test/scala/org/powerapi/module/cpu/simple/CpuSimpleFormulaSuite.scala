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
package org.powerapi.module.cpu.simple

import java.util.UUID

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import akka.actor.Props
import akka.pattern.gracefulStop
import akka.testkit.{EventFilter, TestActorRef}
import akka.util.Timeout

import org.powerapi.UnitTest
import org.powerapi.core.power._
import org.powerapi.core.target.{Target, TargetUsageRatio, intToProcess}
import org.powerapi.core.{MessageBus, Tick}
import org.powerapi.module.FormulaChannel.{startFormula, stopFormula}
import org.powerapi.module.Formulas
import org.powerapi.module.PowerChannel.{RawPowerReport, subscribeRawPowerReport}
import org.powerapi.module.cpu.UsageMetricsChannel.publishUsageReport

class CpuSimpleFormulaSuite extends UnitTest {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.terminate()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A CpuSimpleFormula" should "process a SensorReport and then publish a RawPowerReport" in new Bus {
    val tdp = 220.0
    val tdpFactor = 0.7
    val muid = UUID.randomUUID()
    val target: Target = 1
    val targetRatio = TargetUsageRatio(0.4)

    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }

    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }

    val formulas = TestActorRef(Props(classOf[Formulas], eventBus), "formulas")
    EventFilter.info(occurrences = 1, start = s"formula is started, class: ${classOf[CpuSimpleFormula].getName}").intercept({
      startFormula(muid, target, classOf[CpuSimpleFormula], Seq(eventBus, muid, target, tdp, tdpFactor))(eventBus)
    })
    subscribeRawPowerReport(muid)(eventBus)(testActor)

    publishUsageReport(muid, target, targetRatio, tick1)(eventBus)

    val rawPowerReport = expectMsgClass(classOf[RawPowerReport])
    rawPowerReport.muid should equal(muid)
    rawPowerReport.target should equal(target)
    rawPowerReport.power should equal((tdp * tdpFactor * targetRatio.ratio).W)
    rawPowerReport.device should equal("cpu")
    rawPowerReport.tick should equal(tick1)

    EventFilter.info(occurrences = 1, start = s"formula is stopped, class: ${classOf[CpuSimpleFormula].getName}").intercept({
      stopFormula(muid)(eventBus)
    })

    publishUsageReport(muid, target, targetRatio, tick2)(eventBus)
    expectNoMsg()

    Await.result(gracefulStop(formulas, timeout.duration), timeout.duration)
  }
}
