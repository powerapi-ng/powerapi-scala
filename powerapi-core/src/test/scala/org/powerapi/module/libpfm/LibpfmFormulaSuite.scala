/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2015 Inria, University of Lille 1.
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

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestActorRef, TestKit}
import org.powerapi.UnitTest
import org.powerapi.core.ClockChannel.ClockTick
import org.powerapi.core.target.Process
import org.powerapi.core.power._
import org.powerapi.module.libpfm.PerformanceCounterChannel.{PCWrapper, publishPCReport}
import org.powerapi.module.PowerChannel.{RawPowerReport, subscribeRawPowerReport}
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class LibpfmFormulaSuite(system: ActorSystem) extends UnitTest(system) {

  import org.powerapi.core.MessageBus

  def this() = this(ActorSystem("LibpfmFormulaSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  trait Formulae {
    val formula = Map[String, Double](
      "REQUESTS_TO_L2:CANCELLED" -> 8.002e-09,
      "REQUESTS_TO_L2:ALL" -> 1.251e-08,
      "LS_DISPATCH:STORES" -> 3.520e-09,
      "LS_DISPATCH:ALL" -> 6.695e-09,
      "LS_DISPATCH:LOADS" -> 9.504e-09
    )
  }

  "A LibpfmFormula" should "compute the power" in new Bus with Formulae {
    val actor = TestActorRef(Props(classOf[LibpfmFormula], eventBus, formula, 1.seconds), "libpfm-formula1")(system)
    val muid = UUID.randomUUID()
    val tick1 = ClockTick("clock", 1.seconds)
    val tick2 = ClockTick("clock", 250.milliseconds)
    subscribeRawPowerReport(muid)(eventBus)(testActor)

    val wrappers1 = List[PCWrapper](
      PCWrapper(0, "REQUESTS_TO_L2:CANCELLED", List(Future[Long] {1000000000l}, Future[Long] {5000000l})),
      PCWrapper(1, "REQUESTS_TO_L2:CANCELLED", List(Future[Long] {0l}, Future[Long] {100000l})),
      PCWrapper(0, "REQUESTS_TO_L2:ALL", List(Future[Long] {33000l}, Future[Long] {11000l})),
      PCWrapper(1, "REQUESTS_TO_L2:ALL", List(Future[Long] {2000000000l}, Future[Long] {0l})),
      PCWrapper(0, "LS_DISPATCH:ALL", List(Future[Long] {500000000l}, Future[Long] {1000000000l})),
      PCWrapper(1, "LS_DISPATCH:ALL", List(Future[Long] {500000000l}, Future[Long] {1000000000l})),
      PCWrapper(0, "LS_DISPATCH:LOADS", List(Future[Long] {20000l}, Future[Long] {30000l})),
      PCWrapper(1, "LS_DISPATCH:LOADS", List(Future[Long] {25000l}, Future[Long] {0l}))
    )

    val wrappers2 = List[PCWrapper](
      PCWrapper(0, "REQUESTS_TO_L2:CANCELLED", List(Future[Long] {math.round(1000000000l / 4.0)}, Future[Long] {math.round(5000000l / 4.0)})),
      PCWrapper(1, "REQUESTS_TO_L2:CANCELLED", List(Future[Long] {0l}, Future[Long] {math.round(100000l / 4.0)})),
      PCWrapper(0, "REQUESTS_TO_L2:ALL", List(Future[Long] {math.round(33000l / 4.0)}, Future[Long] {math.round(11000l / 4.0)})),
      PCWrapper(1, "REQUESTS_TO_L2:ALL", List(Future[Long] {math.round(2000000000l / 4.0)}, Future[Long] {0l})),
      PCWrapper(0, "LS_DISPATCH:ALL", List(Future[Long] {math.round(500000000l / 4.0)}, Future[Long] {math.round(1000000000l / 4.0)})),
      PCWrapper(1, "LS_DISPATCH:ALL", List(Future[Long] {math.round(500000000l / 4.0)}, Future[Long] {math.round(1000000000l / 4.0)})),
      PCWrapper(0, "LS_DISPATCH:LOADS", List(Future[Long] {math.round(20000l / 4.0)}, Future[Long] {math.round(30000l / 4.0)})),
      PCWrapper(1, "LS_DISPATCH:LOADS", List(Future[Long] {math.round(25000l / 4.0)}, Future[Long] {0l}))
    )

    var power = 0d

    // REQUESTS_TO_L2:CANCELLED
    power += formula("REQUESTS_TO_L2:CANCELLED") * (1000000000l + 5000000l + 0l + 100000l)
    // REQUESTS_TO_L2:ALL
    power += formula("REQUESTS_TO_L2:ALL") * (33000l + 11000l + 2000000000l + 0l)
    // LS_DISPATCH:ALL
    power += formula("LS_DISPATCH:ALL") * (500000000l + 1000000000l + 500000000l + 1000000000l)
    // LS_DISPATCH:LOADS
    power += formula("LS_DISPATCH:LOADS") * (20000l + 30000l + 25000l + 0l)

    publishPCReport(muid, 1, wrappers1, tick1)(eventBus)
    val ret1 = expectMsgClass(classOf[RawPowerReport])
    ret1.muid should equal(muid)
    ret1.target should equal(Process(1))
    ret1.power.toWatts shouldBe power +- 0.001
    ret1.device should equal("cpu")
    ret1.tick should equal(tick1)

    publishPCReport(muid, 1, wrappers2, tick2)(eventBus)
    val ret2 = expectMsgClass(classOf[RawPowerReport])
    ret2.muid should equal(muid)
    ret2.target should equal(Process(1))
    ret2.power.toWatts shouldBe power +- 0.001
    ret2.device should equal("cpu")
    ret2.tick should equal(tick2)
  }
}
