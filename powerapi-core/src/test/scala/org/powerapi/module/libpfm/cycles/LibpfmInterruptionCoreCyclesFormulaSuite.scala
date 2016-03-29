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

import java.util.UUID

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import akka.actor.Props
import akka.testkit.{EventFilter, TestActorRef}
import akka.pattern.gracefulStop
import akka.util.Timeout

import org.powerapi.UnitTest
import org.powerapi.core.{Tick, MessageBus}
import org.powerapi.core.power._
import org.powerapi.core.target.Process
import org.powerapi.module.FormulaChannel.{stopFormula, startFormula}
import org.powerapi.module.Formulas
import org.powerapi.module.PowerChannel.{RawPowerReport, subscribeRawPowerReport}
import org.powerapi.module.libpfm.PCInterruptionChannel.{InterruptionTick, InterruptionHWCounter, InterruptionPCWrapper, publishInterruptionPCReport}
import org.powerapi.module.libpfm.TID

class LibpfmInterruptionCoreCyclesFormulaSuite extends UnitTest {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.shutdown()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  trait Formulae {
    var formulae = Map[Double, List[Double]]()
    formulae += 12d -> List(85.7545270697, 1.10006565433e-08, -2.0341944068e-18)
    formulae += 13d -> List(87.0324917754, 9.03486530986e-09, -1.31575869787e-18)
    formulae += 14d -> List(86.3094440375, 1.04895773556e-08, -1.61982669617e-18)
    formulae += 15d -> List(88.2194900717, 8.71468661777e-09, -1.12354133527e-18)
    formulae += 16d -> List(85.8010062547, 1.05239105674e-08, -1.34813984791e-18)
    formulae += 17d -> List(85.5127064474, 1.05732955159e-08, -1.28040830962e-18)
    formulae += 18d -> List(85.5593567382, 1.07921513277e-08, -1.22419197787e-18)
    formulae += 19d -> List(87.2004521609, 9.99728883739e-09, -9.9514346029e-19)
    formulae += 20d -> List(87.7358230435, 1.00553994023e-08, -1.00002335486e-18)
    formulae += 21d -> List(94.4635683042, 4.83140424765e-09, 4.25218895447e-20)
    formulae += 22d -> List(104.356371072, 3.75414807806e-09, 6.73289818651e-20)
  }

  "A LibpfmInterruptionCoreCyclesFormula" should "process a SensorReport and then publish a RawPowerReport" in new Bus with Formulae {
    val muid = UUID.randomUUID()
    val target = Process(1)

    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.nanoTime()
    }

    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.nanoTime() + 1000
    }

    var wrappers = Seq[InterruptionPCWrapper]()
    wrappers +:= InterruptionPCWrapper(0, "thread_p", Seq(InterruptionHWCounter(0, 10, "main.a.b", 650000000, false), InterruptionHWCounter(1, 11, "main.z", 651000000, false)))
    wrappers +:= InterruptionPCWrapper(0, "ref_p", Seq(InterruptionHWCounter(0, 10, "main.a.b", 34475589, false), InterruptionHWCounter(1, 11, "main.z", 34075589, false)))
    wrappers +:= InterruptionPCWrapper(1, "thread_p", Seq(InterruptionHWCounter(2, 12, "main.e.e.e", 240000000, true)))
    wrappers +:= InterruptionPCWrapper(1, "ref_p", Seq(InterruptionHWCounter(2, 12, "main.e.e.e", 15475589, true)))

    val formulas = TestActorRef(Props(classOf[Formulas], eventBus), "formulas")
    EventFilter.info(occurrences = 1, start = s"formula is started, class: ${classOf[LibpfmInterruptionCoreCyclesFormula].getName}").intercept({
      startFormula(muid, target, classOf[LibpfmInterruptionCoreCyclesFormula], Seq(eventBus, muid, target, "thread_p", "ref_p", formulae, 250.millis))(eventBus)
    })
    subscribeRawPowerReport(muid)(eventBus)(testActor)

    publishInterruptionPCReport(muid, target, wrappers, tick1)(eventBus)
    val messages = receiveN(3).asInstanceOf[Seq[RawPowerReport]]
    val msg1 = messages.filter(_.tick.asInstanceOf[InterruptionTick].tid == TID(10)).head
    val tickMsg1 = msg1.tick.asInstanceOf[InterruptionTick]
    msg1.muid should equal(muid)
    msg1.target should equal(target)
    msg1.power should be > 0.W
    msg1.device should equal("cpu")
    tickMsg1.cpu should equal(0)
    tickMsg1.fullMethodName should equal("main.a.b")
    tickMsg1.tid should equal(TID(10))
    tickMsg1.timestamp should equal(tick1.timestamp)
    tickMsg1.triggering should equal(false)
    val msg2 = messages.filter(_.tick.asInstanceOf[InterruptionTick].tid == TID(11)).head
    val tickMsg2 = msg2.tick.asInstanceOf[InterruptionTick]
    msg2.muid should equal(muid)
    msg2.target should equal(target)
    msg2.power should be > 0.W
    msg2.device should equal("cpu")
    tickMsg2.cpu should equal(1)
    tickMsg2.fullMethodName should equal("main.z")
    tickMsg2.tid should equal(TID(11))
    tickMsg2.timestamp should equal(tick1.timestamp)
    tickMsg2.triggering should equal(false)
    val msg3 = messages.filter(_.tick.asInstanceOf[InterruptionTick].tid == TID(12)).head
    val tickMsg3 = msg3.tick.asInstanceOf[InterruptionTick]
    msg3.muid should equal(muid)
    msg3.target should equal(target)
    msg3.power should be > 0.W
    msg3.device should equal("cpu")
    tickMsg3.cpu should equal(2)
    tickMsg3.fullMethodName should equal("main.e.e.e")
    tickMsg3.tid should equal(TID(12))
    tickMsg3.timestamp should equal(tick1.timestamp)
    tickMsg3.triggering should equal(true)

    EventFilter.info(occurrences = 1, start = s"formula is stopped, class: ${classOf[LibpfmInterruptionCoreCyclesFormula].getName}").intercept({
      stopFormula(muid)(eventBus)
    })

    publishInterruptionPCReport(muid, target, wrappers, tick2)(eventBus)
    expectNoMsg()

    Await.result(gracefulStop(formulas, timeout.duration), timeout.duration)
  }
}
