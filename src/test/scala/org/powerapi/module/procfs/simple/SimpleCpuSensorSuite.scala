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
package org.powerapi.module.procfs.simple

import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.MessageBus

import scala.concurrent.duration.DurationInt

class SimpleCpuSensorSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("SimpleCpuSensorSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val eventBus = new MessageBus

  "A simple CpuSensor" should "process a MonitorTick message if the target is a Process or an Application and then publish a UsageReport" in {
    import akka.pattern.gracefulStop
    import org.powerapi.core.{All, Application, OSHelper, Process, TargetUsageRatio, Thread, TimeInStates}
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.core.MonitorChannel.publishMonitorTick
    import org.powerapi.module.CacheKey
    import org.powerapi.module.procfs.ProcMetricsChannel.UsageReport
    import org.powerapi.module.procfs.ProcMetricsChannel.subscribeSimpleUsageReport

    val globalElapsedTime = 43171 + 1 + 24917 + 25883594 + 1160 + 19 + 1477 + 0
    val p1ElapsedTime = 33 + 2
    val p2ElapsedTime = 10 + 5
    val p3ElapsedTime = 3 + 5
    val appElapsedTime = p2ElapsedTime + p3ElapsedTime

    val cpuSensor = TestActorRef(Props(classOf[CpuSensor], eventBus, new OSHelper {
      def getProcesses(application: Application): List[Process] = List(Process(2), Process(3))

      def getThreads(process: Process): List[Thread] = List()

      def getProcessCpuTime(process: Process): Option[Long] = {
        process match {
          case Process(1) => Some(p1ElapsedTime)
          case Process(2) => Some(p2ElapsedTime)
          case Process(3) => Some(p3ElapsedTime)
          case _ => None
        }
      }

      def getGlobalCpuTime: Option[Long] = Some(globalElapsedTime)

      def getTimeInStates: TimeInStates = TimeInStates(Map())
    }), "simple-CpuSensor1")(system)

    val oldP1ElapsedTime = p1ElapsedTime / 2
    val oldAppElapsedTime = appElapsedTime / 2
    val oldGlobalElapsedTime = globalElapsedTime / 2

    val muid1 = UUID.randomUUID()
    val muid2 = UUID.randomUUID()
    val tickMock = ClockTick("test", 25.milliseconds)

    val processRatio = TargetUsageRatio((p1ElapsedTime - oldP1ElapsedTime).toDouble / (globalElapsedTime - oldGlobalElapsedTime))
    val appRatio = TargetUsageRatio((appElapsedTime - oldAppElapsedTime).toDouble / (globalElapsedTime - oldGlobalElapsedTime))

    subscribeSimpleUsageReport(eventBus)(testActor)

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache.update(CacheKey(muid1, Process(1)), (oldP1ElapsedTime, oldGlobalElapsedTime))
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache.update(CacheKey(muid2, Process(1)), (oldP1ElapsedTime, oldGlobalElapsedTime))
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache.update(CacheKey(muid2, Application("app")), (oldAppElapsedTime, oldGlobalElapsedTime))

    publishMonitorTick(muid1, Process(1), tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid1); ur.target should equal(Process(1)); ur.targetRatio should equal(processRatio)
    }
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache.getOrElse(CacheKey(muid1, Process(1)), (0, 0)) match {
      case times => times should equal(p1ElapsedTime, globalElapsedTime)
    }

    publishMonitorTick(muid2, Process(1), tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid2); ur.target should equal(Process(1)); ur.targetRatio should equal(processRatio)
    }
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache.getOrElse(CacheKey(muid2, Process(1)), (0, 0)) match {
      case times => times should equal(p1ElapsedTime, globalElapsedTime)
    }

    publishMonitorTick(muid2, Application("app"), tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid2); ur.target should equal(Application("app")); ur.targetRatio should equal(appRatio)
    }
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache.getOrElse(CacheKey(muid2, Application("app")), (0, 0)) match {
      case times => times should equal(appElapsedTime, globalElapsedTime)
    }

    publishMonitorTick(muid1, All, tickMock)(eventBus)
    expectNoMsg()
    gracefulStop(cpuSensor, 1.seconds)
  }

  it should "handle correctly the time differences for computing the TargetUsageRatio" in {
    import akka.pattern.gracefulStop
    import org.powerapi.core.{Application, OSHelper, Process, TargetUsageRatio, Thread, TimeInStates}
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.core.MonitorChannel.publishMonitorTick
    import org.powerapi.module.CacheKey
    import org.powerapi.module.procfs.ProcMetricsChannel.UsageReport
    import org.powerapi.module.procfs.ProcMetricsChannel.subscribeSimpleUsageReport

    val globalElapsedTime = 43171 + 1 + 24917 + 25883594 + 1160 + 19 + 1477 + 0
    val p1ElapsedTime = 33 + 2

    val cpuSensor = TestActorRef(Props(classOf[CpuSensor], eventBus, new OSHelper {
      def getProcesses(application: Application): List[Process] = List()

      def getThreads(process: Process): List[Thread] = List()

      def getProcessCpuTime(process: Process): Option[Long] = {
        process match {
          case Process(1) => Some(p1ElapsedTime)
          case _ => None
        }
      }

      def getGlobalCpuTime: Option[Long] = Some(globalElapsedTime)

      def getTimeInStates: TimeInStates = TimeInStates(Map())
    }), "simple-CpuSensor2")(system)

    val oldP1ElapsedTime = p1ElapsedTime / 2
    val oldGlobalElapsedTime = globalElapsedTime / 2

    val muid = UUID.randomUUID()
    val tickMock = ClockTick("test", 25.milliseconds)

    subscribeSimpleUsageReport(eventBus)(testActor)

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache.update(CacheKey(muid, Process(1)), (p1ElapsedTime + 10, globalElapsedTime))
    publishMonitorTick(muid, Process(1), tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid); ur.target should equal(Process(1)); ur.targetRatio should equal(TargetUsageRatio(0.0))
    }
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache.getOrElse(CacheKey(muid, Process(1)), (0, 0)) match {
      case times => times should equal(p1ElapsedTime + 10, globalElapsedTime)
    }

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache.update(CacheKey(muid, Process(1)), (p1ElapsedTime , globalElapsedTime + 10))
    publishMonitorTick(muid, Process(1), tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid); ur.target should equal(Process(1)); ur.targetRatio should equal(TargetUsageRatio(0.0))
    }
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache.getOrElse(CacheKey(muid, Process(1)), (0, 0)) match {
      case times => times should equal(p1ElapsedTime , globalElapsedTime + 10)
    }

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache.update(CacheKey(muid, Process(1)), (globalElapsedTime, p1ElapsedTime))
    publishMonitorTick(muid, Process(1), tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid); ur.target should equal(Process(1)); ur.targetRatio should equal(TargetUsageRatio(0.0))
    }
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache.getOrElse(CacheKey(muid, Process(1)), (0, 0)) match {
      case times => times should equal(globalElapsedTime, p1ElapsedTime)
    }
    gracefulStop(cpuSensor, 1.seconds)
  }
}
