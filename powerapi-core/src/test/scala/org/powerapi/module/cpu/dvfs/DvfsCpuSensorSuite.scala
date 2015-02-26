/*
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
 *
 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */
package org.powerapi.module.cpu.dvfs

import akka.actor.{ActorSystem, Props}
import akka.pattern.gracefulStop
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import java.util.UUID
import org.powerapi.UnitTest
import org.powerapi.core.MessageBus
import org.powerapi.core.ClockChannel.ClockTick
import org.powerapi.core.{OSHelper, Thread, TimeInStates}
import org.powerapi.core.target.{All, Application, intToProcess, stringToApplication, Process}
import org.powerapi.core.MonitorChannel.publishMonitorTick
import org.powerapi.module.CacheKey
import org.powerapi.module.cpu.UsageMetricsChannel.UsageReport
import org.powerapi.module.cpu.UsageMetricsChannel.subscribeDvfsUsageReport

import scala.concurrent.duration.DurationInt

class DvfsCpuSensorSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("DvfsCpuSensorSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val eventBus = new MessageBus

  "A dvfs CpuSensor" should "process a MonitorTick message and then publish a UsageReport" in {
    val oldTimeInStates = TimeInStates(Map(4000000l -> 10l, 3000000l -> 10l, 2000000l -> 6l, 1000000l -> 2l))
    val timeInStates = TimeInStates(Map(4000000l -> 16l, 3000000l -> 12l, 2000000l -> 8l, 1000000l -> 4l))

    val cpuSensor = TestActorRef(Props(classOf[CpuSensor], eventBus, new OSHelper {
      import org.powerapi.core.GlobalCpuTime

      private var times = List(oldTimeInStates, oldTimeInStates, oldTimeInStates, timeInStates, timeInStates, timeInStates)

      def getCPUFrequencies(topology: Map[Int, Iterable[Int]]): Iterable[Long] = Iterable()

      def getProcesses(application: Application): Iterable[Process] = Iterable()

      def getThreads(process: Process): Iterable[Thread] = Iterable()

      def getProcessCpuTime(process: Process): Option[Long] = None

      def getGlobalCpuTime: GlobalCpuTime = GlobalCpuTime(0, 0)

      def getTimeInStates: TimeInStates = {
        times.headOption match {
          case Some(timeInState) => {
            times = times.tail
            timeInState
          }
          case _ => TimeInStates(Map())
        }
      }
    }), "dvfs-CpuSensor1")(system)

    val muid = UUID.randomUUID()
    val tickMock = ClockTick("test", 25.milliseconds)

    subscribeDvfsUsageReport(eventBus)(testActor)

    publishMonitorTick(muid, 1, tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid); ur.target should equal(Process(1)); ur.timeInStates should equal(TimeInStates(Map(4000000l -> 0l, 3000000l -> 0l, 2000000l -> 0l, 1000000l -> 0l)))
    }
    publishMonitorTick(muid, "app", tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid); ur.target should equal(Application("app")); ur.timeInStates should equal(TimeInStates(Map(4000000l -> 0l, 3000000l -> 0l, 2000000l -> 0l, 1000000l -> 0l)))
    }
    publishMonitorTick(muid, All, tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid); ur.target should equal(All); ur.timeInStates should equal(TimeInStates(Map(4000000l -> 0l, 3000000l -> 0l, 2000000l -> 0l, 1000000l -> 0l)))
    }

    publishMonitorTick(muid, 1, tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid); ur.target should equal(Process(1)); ur.timeInStates should equal(TimeInStates(Map(4000000l -> 6l, 3000000l -> 2l, 2000000l -> 2l, 1000000l -> 2l)))
    }
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].frequenciesCache(CacheKey(muid, 1))(TimeInStates(Map())) match {
      case t => t should equal(TimeInStates(Map(4000000l -> 16l, 3000000l -> 12l, 2000000l -> 8l, 1000000l -> 4l)))
    }
    publishMonitorTick(muid, "app", tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid); ur.target should equal(Application("app")); ur.timeInStates should equal(TimeInStates(Map(4000000l -> 6l, 3000000l -> 2l, 2000000l -> 2l, 1000000l -> 2l)))
    }
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].frequenciesCache(CacheKey(muid, "app"))(TimeInStates(Map())) match {
      case t => t should equal(TimeInStates(Map(4000000l -> 16l, 3000000l -> 12l, 2000000l -> 8l, 1000000l -> 4l)))
    }
    publishMonitorTick(muid, All, tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid); ur.target should equal(All); ur.timeInStates should equal(TimeInStates(Map(4000000l -> 6l, 3000000l -> 2l, 2000000l -> 2l, 1000000l -> 2l)))
    }

    gracefulStop(cpuSensor, 1.seconds)
  }

  it should "handle correctly the TimeInStates differences for computing the current TimeInStates" in {
    val oldTimeInStates = TimeInStates(Map(4000000l -> 20l, 3000000l -> 20l, 2000000l -> 20l, 1000000l -> 20l))
    val timeInStates = TimeInStates(Map(4000000l -> 16l, 3000000l -> 12l, 2000000l -> 8l, 1000000l -> 4l))

    val cpuSensor = TestActorRef(Props(classOf[CpuSensor], eventBus, new OSHelper {
      import org.powerapi.core.GlobalCpuTime

      private var times = List(oldTimeInStates, timeInStates)

      def getCPUFrequencies(topology: Map[Int, Iterable[Int]]): Iterable[Long] = Iterable()

      def getProcesses(application: Application): List[Process] = List()

      def getThreads(process: Process): List[Thread] = List()

      def getProcessCpuTime(process: Process): Option[Long] = None

      def getGlobalCpuTime: GlobalCpuTime = GlobalCpuTime(0, 0)

      def getTimeInStates: TimeInStates = {
        times.headOption match {
          case Some(timeInState) => {
            times = times.tail
            timeInState
          }
          case _ => TimeInStates(Map())
        }
      }
    }), "dvfs-CpuSensor2")(system)

    val muid = UUID.randomUUID()
    val tickMock = ClockTick("test", 25.milliseconds)

    subscribeDvfsUsageReport(eventBus)(testActor)

    publishMonitorTick(muid, 1, tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid); ur.target should equal(Process(1)); ur.timeInStates should equal(TimeInStates(Map(4000000l -> 0l, 3000000l -> 0l, 2000000l -> 0l, 1000000l -> 0l)))
    }
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].frequenciesCache(CacheKey(muid, 1))(TimeInStates(Map())) match {
      case t => t should equal(TimeInStates(Map(4000000l -> 20l, 3000000l -> 20l, 2000000l -> 20l, 1000000l -> 20l)))
    }

    publishMonitorTick(muid, 1, tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid); ur.target should equal(Process(1)); ur.timeInStates should equal(TimeInStates(Map(4000000l -> 0l, 3000000l -> 0l, 2000000l -> 0l, 1000000l -> 0l)))
    }
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].frequenciesCache(CacheKey(muid, 1))(TimeInStates(Map())) match {
      case t => t should equal(TimeInStates(Map(4000000l -> 20l, 3000000l -> 20l, 2000000l -> 20l, 1000000l -> 20l)))
    }

    gracefulStop(cpuSensor, 1.seconds)
  }
}
