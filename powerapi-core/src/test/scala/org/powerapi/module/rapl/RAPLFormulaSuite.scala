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

import java.util.UUID

import akka.actor.Props
import akka.pattern.gracefulStop
import akka.testkit.{EventFilter, TestActorRef}
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.power._
import org.powerapi.core.target.{All, Target}
import org.powerapi.core.{MessageBus, Tick}
import org.powerapi.module.FormulaChannel.{startFormula, stopFormula}
import org.powerapi.module.Formulas
import org.powerapi.module.PowerChannel.{RawPowerReport, subscribeRawPowerReport}
import org.powerapi.module.hwc.RAPLDomain
import org.powerapi.module.rapl.RAPLChannel.publishRAPLReport
import org.scalamock.scalatest.MockFactory

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class RAPLFormulaSuite extends UnitTest with MockFactory {

  val timeout = Timeout(20.seconds)

  override def afterAll() = {
    system.terminate()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A RAPLFormula" should "process a SensorReport and then publish a RawPowerReport" in new Bus {
    val muid = UUID.randomUUID()
    val target: Target = All

    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }

    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 1000
    }

    val formulas = TestActorRef(Props(classOf[Formulas], eventBus), "formulas")
    EventFilter.info(occurrences = 1, start = s"formula is started, class: ${classOf[RAPLFormula].getName}").intercept({
      startFormula(muid, target, classOf[RAPLFormula], Seq(eventBus, muid, target, RAPLDomain.DRAM))(eventBus)
    })
    subscribeRawPowerReport(muid)(eventBus)(testActor)

    val values = Seq(80.40, 20.10)

    Thread.sleep(1000)

    publishRAPLReport(muid, target, RAPLDomain.DRAM, values, tick1)(eventBus)
    val reports = receiveN(2).asInstanceOf[Seq[RawPowerReport]]
    reports.size should equal(2)
    val reportS0 = reports.find(_.device == "rapl-dram-S0").get
    val reportS1 = reports.find(_.device == "rapl-dram-S1").get
    reportS0.muid should equal(muid)
    reportS0.target should equal(target)
    reportS0.power should (be > 0.W and be <= 80.40.W)
    reportS0.device should equal("rapl-dram-S0")
    reportS0.tick should equal(tick1)
    reportS1.muid should equal(muid)
    reportS1.target should equal(target)
    reportS1.power should (be > 0.W and be <= 20.10.W)
    reportS1.device should equal("rapl-dram-S1")
    reportS1.tick should equal(tick1)

    EventFilter.info(occurrences = 1, start = s"formula is stopped, class: ${classOf[RAPLFormula].getName}").intercept({
      stopFormula(muid)(eventBus)
    })

    publishRAPLReport(muid, target, RAPLDomain.DRAM, values, tick2)(eventBus)
    expectNoMsg()

    Await.result(gracefulStop(formulas, timeout.duration), timeout.duration)
  }
}
