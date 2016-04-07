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

import java.util.UUID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

import akka.actor.Props
import akka.pattern.gracefulStop
import akka.testkit.{EventFilter, TestActorRef}
import akka.util.Timeout

import org.powerapi.UnitTest
import org.powerapi.core.power._
import org.powerapi.core.target.Target
import org.powerapi.core.{MessageBus, Tick}
import org.powerapi.module.FormulaChannel.{startFormula, stopFormula}
import org.powerapi.module.Formulas
import org.powerapi.module.PowerChannel.{RawPowerReport, subscribeRawPowerReport}
import org.powerapi.module.libpfm.PerformanceCounterChannel.{HWCounter, PCWrapper, publishPCReport}

class LibpfmFormulaSuite extends UnitTest {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.shutdown()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  trait Formulae {
    val formula = Map[String, Double](
      "REQUESTS_TO_L2:CANCELLED" -> 8.002e-09,
      "REQUESTS_TO_L2:ALL" -> 1.251e-08,
      "LS_DISPATCH:ALL" -> 6.695e-09
    )
  }

  "A LibpfmFormula" should "process a SensorReport and then publish a RawPowerReport" in new Bus with Formulae {
    val muid = UUID.randomUUID()
    val target: Target = 1

    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }

    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }

    val formulas = TestActorRef(Props(classOf[Formulas], eventBus), "formulas")
    EventFilter.info(occurrences = 1, start = s"formula is started, class: ${classOf[LibpfmFormula].getName}").intercept({
      startFormula(muid, target, classOf[LibpfmFormula], Seq(eventBus, muid, target, formula, 250.millis))(eventBus)
    })
    subscribeRawPowerReport(muid)(eventBus)(testActor)

    var wrappers = Seq[PCWrapper]()
    wrappers +:= PCWrapper(0, "REQUESTS_TO_L2:CANCELLED", List[Future[HWCounter]](Future(HWCounter(250000000)), Future(HWCounter(0))))
    wrappers +:= PCWrapper(0, "REQUESTS_TO_L2:ALL", List[Future[HWCounter]](Future(HWCounter(330000)), Future(HWCounter(0))))
    wrappers +:= PCWrapper(0, "LS_DISPATCH:ALL", List[Future[HWCounter]](Future(HWCounter(1000000)), Future(HWCounter(0))))
    wrappers +:= PCWrapper(1, "REQUESTS_TO_L2:CANCELLED", List[Future[HWCounter]](Future(HWCounter(0)), Future(HWCounter(500000000))))
    wrappers +:= PCWrapper(1, "REQUESTS_TO_L2:ALL", List[Future[HWCounter]](Future(HWCounter(0)), Future(HWCounter(220000000))))
    wrappers +:= PCWrapper(1, "LS_DISPATCH:ALL", List[Future[HWCounter]](Future(HWCounter(0)), Future(HWCounter(50000))))

    publishPCReport(muid, target, wrappers, tick1)(eventBus)
    var rawPowerReport = expectMsgClass(classOf[RawPowerReport])
    rawPowerReport.muid should equal(muid)
    rawPowerReport.target should equal(target)
    rawPowerReport.power should be > 0.W
    rawPowerReport.device should equal("cpu")
    rawPowerReport.tick should equal(tick1)

    EventFilter.info(occurrences = 1, start = s"formula is stopped, class: ${classOf[LibpfmFormula].getName}").intercept({
      stopFormula(muid)(eventBus)
    })

    publishPCReport(muid, target, wrappers, tick2)(eventBus)
    expectNoMsg()

    Await.result(gracefulStop(formulas, timeout.duration), timeout.duration)
  }
}
