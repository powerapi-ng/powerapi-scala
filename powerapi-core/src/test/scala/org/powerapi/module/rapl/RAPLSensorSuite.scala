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
import akka.testkit.{EventFilter, TestActorRef}
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.MonitorChannel.publishMonitorTick
import org.powerapi.core.target.{All, Container, Target}
import org.powerapi.core.{MessageBus, Tick}
import org.powerapi.module.rapl.RAPLChannel.{RAPLReport, subscribeRAPLReport}
import org.powerapi.module.SensorChannel.{startSensor, stopSensor}
import org.powerapi.module.Sensors
import org.powerapi.module.hwc.{AffinityDomain, AffinityDomains, HWThread, LikwidHelper, PowerData, RAPLDomain}
import org.scalamock.scalatest.MockFactory

import scala.concurrent.duration.DurationInt

class RAPLSensorSuite extends UnitTest with MockFactory {

  val timeout = Timeout(20.seconds)

  override def afterAll() = {
    system.terminate()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A RAPLSensor for the CPU" should "handle MonitorTick messages and sense RAPL PKG domain for the All target" in new Bus {
    val likwidHelper = mock[LikwidHelper]

    val muid = UUID.randomUUID()
    val target = All
    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }
    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 1000
    }

    val sensors = TestActorRef(Props(classOf[Sensors], eventBus), "sensors")

    likwidHelper.getAffinityDomains _ expects() returning (
      AffinityDomains(-1, -1, -1, -1, -1, -1, Seq(
        AffinityDomain("S0", 2, Seq(0, 2)),
        AffinityDomain("S1", 2, Seq(1, 3)),
        AffinityDomain("N", 2, Seq(0, 1, 2, 3))
      ))
    )
    likwidHelper.powerInit _ expects 0
    likwidHelper.HPMaddThread _ expects 0
    likwidHelper.HPMaddThread _ expects 1
    likwidHelper.powerStart _ expects (0, RAPLDomain.PKG) returning PowerData(RAPLDomain.PKG.id, -1, 0)
    likwidHelper.powerStart _ expects (1, RAPLDomain.PKG) returning PowerData(RAPLDomain.PKG.id, -2, 0)
    likwidHelper.powerStop _ expects (PowerData(RAPLDomain.PKG.id, -1, 0), 0) returning PowerData(RAPLDomain.PKG.id, -1, 1)
    likwidHelper.powerStop _ expects (PowerData(RAPLDomain.PKG.id, -2, 0), 1) returning PowerData(RAPLDomain.PKG.id, -2, 2)
    likwidHelper.getEnergy _ expects PowerData(RAPLDomain.PKG.id, -1, 1) returning 80
    likwidHelper.getEnergy _ expects PowerData(RAPLDomain.PKG.id, -2, 2) returning 40
    likwidHelper.powerFinalize _ expects()

    EventFilter.info(occurrences = 1, start = s"sensor is started, class: ${classOf[RAPLSensor].getName}").intercept({
      startSensor(muid, target, classOf[RAPLSensor], Seq(eventBus, muid, target, likwidHelper, RAPLDomain.PKG))(eventBus)
    })
    subscribeRAPLReport(muid, target, RAPLDomain.PKG)(eventBus)(testActor)

    publishMonitorTick(muid, target, tick1)(eventBus)

    expectMsgClass(classOf[RAPLReport]) match {
      case RAPLReport(_, _, t: Target, energies, _) =>
        t should equal(target)
        energies should contain theSameElementsAs Seq(80, 40)
      case _ =>
    }

    EventFilter.info(occurrences = 1, start = s"sensor is stopped, class: ${classOf[RAPLSensor].getName}").intercept({
      stopSensor(muid)(eventBus)
    })

    publishMonitorTick(muid, All, tick2)(eventBus)
    expectNoMsg()
  }

  "A RAPLSensor for the DRAM" should "handle MonitorTick messages and sense RAPL DRAM domain for the All target" in new Bus {
    val likwidHelper = mock[LikwidHelper]

    val muid = UUID.randomUUID()
    val target = All
    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }
    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 1000
    }

    val sensors = TestActorRef(Props(classOf[Sensors], eventBus), "sensors")

    likwidHelper.getAffinityDomains _ expects() returning (
      AffinityDomains(-1, -1, -1, -1, -1, -1, Seq(
        AffinityDomain("S0", 2, Seq(0, 2)),
        AffinityDomain("S1", 2, Seq(1, 3)),
        AffinityDomain("N", 2, Seq(0, 1, 2, 3))
      ))
      )
    likwidHelper.powerInit _ expects 0
    likwidHelper.HPMaddThread _ expects 0
    likwidHelper.HPMaddThread _ expects 1
    likwidHelper.powerStart _ expects (0, RAPLDomain.DRAM) returning PowerData(RAPLDomain.DRAM.id, -1, 0)
    likwidHelper.powerStart _ expects (1, RAPLDomain.DRAM) returning PowerData(RAPLDomain.DRAM.id, -2, 0)
    likwidHelper.powerStop _ expects (PowerData(RAPLDomain.DRAM.id, -1, 0), 0) returning PowerData(RAPLDomain.DRAM.id, -1, 1)
    likwidHelper.powerStop _ expects (PowerData(RAPLDomain.DRAM.id, -2, 0), 1) returning PowerData(RAPLDomain.DRAM.id, -2, 2)
    likwidHelper.getEnergy _ expects PowerData(RAPLDomain.DRAM.id, -1, 1) returning 80
    likwidHelper.getEnergy _ expects PowerData(RAPLDomain.DRAM.id, -2, 2) returning 40
    likwidHelper.powerFinalize _ expects()

    EventFilter.info(occurrences = 1, start = s"sensor is started, class: ${classOf[RAPLSensor].getName}").intercept({
      startSensor(muid, target, classOf[RAPLSensor], Seq(eventBus, muid, target, likwidHelper, RAPLDomain.DRAM))(eventBus)
    })
    subscribeRAPLReport(muid, target, RAPLDomain.DRAM)(eventBus)(testActor)

    publishMonitorTick(muid, target, tick1)(eventBus)

    expectMsgClass(classOf[RAPLReport]) match {
      case RAPLReport(_, _, t: Target, energies, _) =>
        t should equal(target)
        energies should contain theSameElementsAs Seq(80, 40)
      case _ =>
    }

    EventFilter.info(occurrences = 1, start = s"sensor is stopped, class: ${classOf[RAPLSensor].getName}").intercept({
      stopSensor(muid)(eventBus)
    })

    publishMonitorTick(muid, All, tick2)(eventBus)
    expectNoMsg()
  }
}
