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
package org.powerapi.module.extpowermeter.powerspy

import java.util.UUID

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import akka.actor.Props
import akka.pattern.gracefulStop
import akka.testkit.{EventFilter, TestActorRef}
import akka.util.Timeout

import org.powerapi.UnitTest
import org.powerapi.core.MonitorChannel.publishMonitorTick
import org.powerapi.core.power._
import org.powerapi.core.target.{All, Application, TargetUsageRatio}
import org.powerapi.core.{ExternalPMeter, GlobalCpuTimes, MessageBus, OSHelper, Tick}
import org.powerapi.module.SensorChannel.{startSensor, stopSensor}
import org.powerapi.module.Sensors
import org.powerapi.module.extpowermeter.ExtPowerMeterChannel.{ExtPowerMeterPowerReport, publishPowerSpyRawPowerReport, subscribePowerSpyPowerReport}
import org.scalamock.scalatest.MockFactory

class PowerSpySensorSuite extends UnitTest with MockFactory {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.shutdown()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A PowerSpySensor" should "handle MonitorTick and RawPowerReport messages for the All target" in new Bus {
    val idlePower = 90.W
    val osHelper = mock[OSHelper]
    val extPMeter = mock[ExternalPMeter]

    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }
    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 1000
    }
    val tick3 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 2000
    }
    val muid = UUID.randomUUID()
    val target = All

    extPMeter.init _ expects eventBus
    extPMeter.start _ expects()
    extPMeter.stop _ expects()

    val sensors = TestActorRef(Props(classOf[Sensors], eventBus), "sensors")
    EventFilter.info(occurrences = 1, start = s"sensor is started, class: ${classOf[PowerSpySensor].getName}").intercept({
      startSensor(muid, target, classOf[PowerSpySensor], Seq(eventBus, muid, target, osHelper, extPMeter, idlePower))(eventBus)
    })
    subscribePowerSpyPowerReport(muid, target)(eventBus)(testActor)

    publishPowerSpyRawPowerReport(105.W)(eventBus)
    publishMonitorTick(muid, target, tick1)(eventBus)
    var reportMock = expectMsgClass(classOf[ExtPowerMeterPowerReport])
    reportMock.muid should equal(muid)
    reportMock.target should equal(target)
    reportMock.tick should equal(tick1)
    reportMock.targetRatio should equal(TargetUsageRatio(1))
    reportMock.power should equal(105.W)
    reportMock.source should equal("powerspy")

    publishPowerSpyRawPowerReport(115.W)(eventBus)
    publishMonitorTick(muid, target, tick2)(eventBus)
    reportMock = expectMsgClass(classOf[ExtPowerMeterPowerReport])
    reportMock.muid should equal(muid)
    reportMock.target should equal(target)
    reportMock.tick should equal(tick2)
    reportMock.targetRatio should equal(TargetUsageRatio(1))
    reportMock.power should equal(115.W)
    reportMock.source should equal("powerspy")

    EventFilter.info(occurrences = 1, start = s"sensor is stopped, class: ${classOf[PowerSpySensor].getName}").intercept({
      stopSensor(muid)(eventBus)
    })

    publishMonitorTick(muid, target, tick3)(eventBus)
    publishPowerSpyRawPowerReport(115.W)(eventBus)
    expectNoMsg()

    Await.result(gracefulStop(sensors, timeout.duration), timeout.duration)
  }

  it should "handle MonitorTick and RawPowerReport messages for the other targets" in new Bus {
    val idlePower = 90.W
    val osHelper = mock[OSHelper]
    val extPMeter = mock[ExternalPMeter]

    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }
    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 1000
    }
    val tick3 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 2000
    }
    val muid = UUID.randomUUID()
    val target = Application("firefox")

    extPMeter.init _ expects eventBus
    extPMeter.start _ expects()
    extPMeter.stop _ expects()
    osHelper.getGlobalCpuTimes _ expects() returning GlobalCpuTimes(1000, 100)
    osHelper.getGlobalCpuTimes _ expects() returning GlobalCpuTimes(1003, 101)
    osHelper.getGlobalCpuTimes _ expects() returning GlobalCpuTimes(1003, 105)
    osHelper.getTargetCpuTime _ expects Application("firefox") returning 4 + 4
    osHelper.getTargetCpuTime _ expects Application("firefox") returning 4 + 4
    osHelper.getTargetCpuTime _ expects Application("firefox") returning 5 + 6

    val sensors = TestActorRef(Props(classOf[Sensors], eventBus), "sensors")
    EventFilter.info(occurrences = 1, start = s"sensor is started, class: ${classOf[PowerSpySensor].getName}").intercept({
      startSensor(muid, target, classOf[PowerSpySensor], Seq(eventBus, muid, target, osHelper, extPMeter, idlePower))(eventBus)
    })
    subscribePowerSpyPowerReport(muid, target)(eventBus)(testActor)

    publishPowerSpyRawPowerReport(105.W)(eventBus)
    publishMonitorTick(muid, target, tick1)(eventBus)
    var reportMock = expectMsgClass(classOf[ExtPowerMeterPowerReport])
    reportMock.muid should equal(muid)
    reportMock.target should equal(target)
    reportMock.tick should equal(tick1)
    reportMock.targetRatio should equal(TargetUsageRatio(((4 + 4) - (4 + 4)) / ((1003 + 101) - (1000 + 100)).toDouble))
    reportMock.power should equal(105.W - 90.W)
    reportMock.source should equal("powerspy")

    publishPowerSpyRawPowerReport(115.W)(eventBus)
    publishMonitorTick(muid, target, tick2)(eventBus)
    reportMock = expectMsgClass(classOf[ExtPowerMeterPowerReport])
    reportMock.muid should equal(muid)
    reportMock.target should equal(target)
    reportMock.tick should equal(tick2)
    reportMock.targetRatio should equal(TargetUsageRatio(((5 + 6) - (4 + 4)) / ((1003 + 105) - (1003 + 101)).toDouble))
    reportMock.power should equal(115.W - 90.W)
    reportMock.source should equal("powerspy")

    EventFilter.info(occurrences = 1, start = s"sensor is stopped, class: ${classOf[PowerSpySensor].getName}").intercept({
      stopSensor(muid)(eventBus)
    })

    publishMonitorTick(muid, target, tick3)(eventBus)
    publishPowerSpyRawPowerReport(115.W)(eventBus)
    expectNoMsg()

    Await.result(gracefulStop(sensors, timeout.duration), timeout.duration)
  }
}