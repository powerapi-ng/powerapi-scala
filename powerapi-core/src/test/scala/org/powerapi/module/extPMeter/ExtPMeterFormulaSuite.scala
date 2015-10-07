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
package org.powerapi.module.extPMeter

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestActorRef, TestKit}
import java.util.UUID
import org.powerapi.UnitTest
import org.powerapi.core.power._
import org.powerapi.core.ClockChannel.ClockTick
import org.powerapi.core.{OSHelper, MessageBus, Thread, TimeInStates}
import org.powerapi.core.MonitorChannel.publishMonitorTick
import org.powerapi.core.target.{All, Application, intToProcess, Process, Target, TargetUsageRatio}
import org.powerapi.module.PowerChannel.{RawPowerReport, subscribeRawPowerReport}
import org.powerapi.module.extPMeter.ExtPMeterChannel.publishPMeterPower
import scala.concurrent.duration.DurationInt

class ExtPMeterFormulaSuite(system: ActorSystem) extends UnitTest(system) {

  def this() = this(ActorSystem("ExtPMeterFormulaSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "A ExtPMeterFormula" should "listen ExtPMeterPower/UsageReport messages and produce PowerReport messages" in {
    val eventBus = new MessageBus

    val globalElapsedTime1: Long = 43171 + 1 + 24917 + 25883594 + 1160 + 19 + 1477 + 0
    val activeElapsedTime1: Long = globalElapsedTime1 - 25883594
    val globalElapsedTime2: Long = 43173 + 1 + 24917 + 25883594 + 1160 + 19 + 1477 + 0
    val activeElapsedTime2: Long = globalElapsedTime2 - 25883594
    val p1ElapsedTime: Long = 33 + 2
    val p2ElapsedTime: Long = 10 + 5

    val muid1 = UUID.randomUUID()
    val muid2 = UUID.randomUUID()
    val muid3 = UUID.randomUUID()

    val oldP1ElapsedTime = p1ElapsedTime / 2
    val oldP2ElapsedTime = p2ElapsedTime / 2

    val (oldGlobalElapsedTime1, oldActiveElapsedTime1) = (globalElapsedTime1 / 2, activeElapsedTime1 / 2)
    val (oldGlobalElapsedTime2, oldActiveElapsedTime2) = (globalElapsedTime2 / 2, activeElapsedTime2 / 2)

    val processRatio1 = TargetUsageRatio((p1ElapsedTime - oldP1ElapsedTime).toDouble / (activeElapsedTime1 - oldActiveElapsedTime1))
    val processRatio2 = TargetUsageRatio((p2ElapsedTime - oldP2ElapsedTime).toDouble / (activeElapsedTime2 - oldActiveElapsedTime2))

    TestActorRef(Props(classOf[ExtPMeterFormula], eventBus, new OSHelper {
      import org.powerapi.core.GlobalCpuTime

      private var targetTimes = Map[Target, List[Long]](
        Process(1) -> List(oldP1ElapsedTime, p1ElapsedTime),
        Process(2) -> List(oldP2ElapsedTime, p2ElapsedTime)
      )

      private var globalTimes = List[(Long, Long)](
        (oldGlobalElapsedTime1, oldActiveElapsedTime1), (oldGlobalElapsedTime2, oldActiveElapsedTime2),
        (globalElapsedTime1, activeElapsedTime1), (globalElapsedTime2, activeElapsedTime2)
      )

      def getCPUFrequencies: Set[Long] = Set()

      def getProcesses(application: Application): Set[Process] = Set(Process(2), Process(3))

      def getThreads(process: Process): Set[Thread] = Set()

      def getProcessCpuTime(process: Process): Option[Long] = {
        targetTimes.getOrElse(process, List()) match {
          case times if times.length > 0 => {
            targetTimes += process -> times.tail
            Some(times.head)
          }
          case _ => None
        }
      }

      def getGlobalCpuTime: GlobalCpuTime = {
        globalTimes.headOption match {
          case Some((globalTime, activeTime)) => {
            globalTimes = globalTimes.tail
            GlobalCpuTime(globalTime, activeTime)
          }
          case _ => GlobalCpuTime(0, 0)
        }
      }
      
      def getProcessCpuPercent(muid: UUID, process: Process): TargetUsageRatio = TargetUsageRatio(0.0)

      def getGlobalCpuPercent(muid: UUID): TargetUsageRatio = TargetUsageRatio(0.0)

      def getTimeInStates: TimeInStates = TimeInStates(Map())
    }, 90.W))(system)

    val tickMock = ClockTick("test", 25.milliseconds)
    subscribeRawPowerReport(muid1)(eventBus)(testActor)
    subscribeRawPowerReport(muid2)(eventBus)(testActor)
    subscribeRawPowerReport(muid3)(eventBus)(testActor)

    publishMonitorTick(muid1, 1, tickMock)(eventBus)
    expectNoMsg(3.seconds)

    publishPMeterPower(120.W)(eventBus)

    publishMonitorTick(muid1, 1, tickMock)(eventBus)
    var ret = expectMsgClass(classOf[RawPowerReport])
    ret.muid should equal(muid1)
    ret.target should equal(Process(1))
    ret.power should equal(0.W)
    ret.tick should equal(tickMock)

    publishMonitorTick(muid2, 2, tickMock)(eventBus)
    ret = expectMsgClass(classOf[RawPowerReport])
    ret.muid should equal(muid2)
    ret.target should equal(Process(2))
    ret.power should equal(0.W)
    ret.tick should equal(tickMock)

    publishMonitorTick(muid3, All, tickMock)(eventBus)
    ret = expectMsgClass(classOf[RawPowerReport])
    ret.muid should equal(muid3)
    ret.target should equal(All)
    ret.power should equal(120.W)
    ret.tick should equal(tickMock)

    publishPMeterPower(150.W)(eventBus)

    publishMonitorTick(muid1, 1, tickMock)(eventBus)
    ret = expectMsgClass(classOf[RawPowerReport])
    ret.muid should equal(muid1)
    ret.target should equal(Process(1))
    ret.power should equal(((150 - 90) * processRatio1.ratio).W)
    ret.tick should equal(tickMock)
    publishMonitorTick(muid2, 2, tickMock)(eventBus)
    ret = expectMsgClass(classOf[RawPowerReport])
    ret.muid should equal(muid2)
    ret.target should equal(Process(2))
    ret.power should equal((processRatio2.ratio * (150 - 90)).W)
    ret.tick should equal(tickMock)

    publishPMeterPower(140.W)(eventBus)

    publishMonitorTick(muid3, All, tickMock)(eventBus)
    ret = expectMsgClass(classOf[RawPowerReport])
    ret.muid should equal(muid3)
    ret.target should equal(All)
    ret.power should equal(140.W)
    ret.tick should equal(tickMock)
  }
}
