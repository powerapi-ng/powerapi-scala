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

import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.MessageBus

import scala.concurrent.duration.DurationInt

class DvfsCpuSensorSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("DvfsCpuSensorSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val eventBus = new MessageBus

  "A dvfs CpuSensor" should "process a MonitorTick message and then publish a UsageReport" in {
    import akka.pattern.gracefulStop
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.core.{All, Application, OSHelper, Process, TargetUsageRatio, Thread}
    import org.powerapi.core.MonitorChannel.publishMonitorTick
    import org.powerapi.core.TimeInStates
    import org.powerapi.module.CacheKey
    import org.powerapi.module.cpu.UsageMetricsChannel.UsageReport
    import org.powerapi.module.cpu.UsageMetricsChannel.subscribeDvfsUsageReport

    val timeInStates = TimeInStates(Map(4000000l -> 16l, 3000000l -> 12l, 2000000l -> 8l, 1000000l -> 4l))

    val cpuSensor = TestActorRef(Props(classOf[CpuSensor], eventBus, new OSHelper {
      def getProcesses(application: Application): List[Process] = List()

      def getThreads(process: Process): List[Thread] = List()

      def getProcessCpuTime(process: Process): Option[Long] = None

      def getGlobalCpuTime: Option[Long] = None

      def getTimeInStates: TimeInStates = timeInStates
    }), "dvfs-CpuSensor1")(system)

    val muid = UUID.randomUUID()
    val tickMock = ClockTick("test", 25.milliseconds)

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].frequenciesCache(CacheKey(muid, Process(1))) = TimeInStates(Map(4000000l -> 10l, 3000000l -> 10l, 2000000l -> 6l, 1000000l -> 2l))
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].frequenciesCache(CacheKey(muid, Application("app"))) = TimeInStates(Map(4000000l -> 10l, 3000000l -> 10l, 2000000l -> 6l, 1000000l -> 2l))
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].frequenciesCache(CacheKey(muid, All)) = TimeInStates(Map(4000000l -> 10l, 3000000l -> 10l, 2000000l -> 6l, 1000000l -> 2l))

    subscribeDvfsUsageReport(eventBus)(testActor)

    publishMonitorTick(muid, Process(1), tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid); ur.target should equal(Process(1)); ur.timeInStates should equal(TimeInStates(Map(4000000l -> 6l, 3000000l -> 2l, 2000000l -> 2l, 1000000l -> 2l)))
    }
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].frequenciesCache(CacheKey(muid, Process(1)))(TimeInStates(Map())) match {
      case t => t should equal(TimeInStates(Map(4000000l -> 16l, 3000000l -> 12l, 2000000l -> 8l, 1000000l -> 4l)))
    }

    publishMonitorTick(muid, Application("app"), tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid); ur.target should equal(Application("app")); ur.timeInStates should equal(TimeInStates(Map(4000000l -> 6l, 3000000l -> 2l, 2000000l -> 2l, 1000000l -> 2l)))
    }
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].frequenciesCache(CacheKey(muid, Application("app")))(TimeInStates(Map())) match {
      case t => t should equal(TimeInStates(Map(4000000l -> 16l, 3000000l -> 12l, 2000000l -> 8l, 1000000l -> 4l)))
    }

    publishMonitorTick(muid, All, tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid); ur.target should equal(All); ur.timeInStates should equal(TimeInStates(Map(4000000l -> 6l, 3000000l -> 2l, 2000000l -> 2l, 1000000l -> 2l)))
    }
    gracefulStop(cpuSensor, 1.seconds)
  }

  it should "handle correctly the TimeInStates differences for computing the current TimeInStates" in {
    import akka.pattern.gracefulStop
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.core.{Application, OSHelper, Process, Thread}
    import org.powerapi.core.MonitorChannel.publishMonitorTick
    import org.powerapi.core.TimeInStates
    import org.powerapi.module.CacheKey
    import org.powerapi.module.cpu.UsageMetricsChannel.UsageReport
    import org.powerapi.module.cpu.UsageMetricsChannel.subscribeDvfsUsageReport

    val timeInStates = TimeInStates(Map(4000000l -> 16l, 3000000l -> 12l, 2000000l -> 8l, 1000000l -> 4l))

    val cpuSensor = TestActorRef(Props(classOf[CpuSensor], eventBus, new OSHelper {
      def getProcesses(application: Application): List[Process] = List()

      def getThreads(process: Process): List[Thread] = List()

      def getProcessCpuTime(process: Process): Option[Long] = None

      def getGlobalCpuTime: Option[Long] = None

      def getTimeInStates: TimeInStates = timeInStates
    }), "dvfs-CpuSensor2")(system)

    val muid = UUID.randomUUID()
    val tickMock = ClockTick("test", 25.milliseconds)

    subscribeDvfsUsageReport(eventBus)(testActor)

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].frequenciesCache(CacheKey(muid, Process(1))) = TimeInStates(Map(4000000l -> 20l, 3000000l -> 20l, 2000000l -> 20l, 1000000l -> 20l))

    publishMonitorTick(muid, Process(1), tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid); ur.target should equal(Process(1)); ur.timeInStates should equal(TimeInStates(Map()))
    }
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].frequenciesCache(CacheKey(muid, Process(1)))(TimeInStates(Map())) match {
      case t => t should equal(TimeInStates(Map(4000000l -> 20l, 3000000l -> 20l, 2000000l -> 20l, 1000000l -> 20l)))
    }
    gracefulStop(cpuSensor, 1.seconds)
  }
}
