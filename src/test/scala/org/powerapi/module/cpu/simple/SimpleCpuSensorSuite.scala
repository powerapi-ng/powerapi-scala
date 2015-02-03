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
package org.powerapi.module.cpu.simple

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

  "A simple CpuSensor" should "process a MonitorTick message and then publish a UsageReport" in {
    import akka.pattern.gracefulStop
    import org.powerapi.core.{OSHelper, Thread, TimeInStates}
    import org.powerapi.core.target.{All, Application, intToProcess, Process, stringToApplication, Target, TargetUsageRatio}
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.core.MonitorChannel.publishMonitorTick
    import org.powerapi.module.CacheKey
    import org.powerapi.module.cpu.UsageMetricsChannel.UsageReport
    import org.powerapi.module.cpu.UsageMetricsChannel.subscribeSimpleUsageReport

    val eventBus = new MessageBus

    val globalElapsedTime1 = 43171 + 1 + 24917 + 25883594 + 1160 + 19 + 1477 + 0
    val globalElapsedTime2 = 43173 + 1 + 24917 + 25883594 + 1160 + 19 + 1477 + 0
    val globalElapsedTime3 = 43175 + 1 + 24917 + 25883594 + 1160 + 19 + 1477 + 0
    val p1ElapsedTime1 = 33 + 2
    val p1ElapsedTime2 = 33 + 4
    val p2ElapsedTime = 10 + 5
    val p3ElapsedTime = 3 + 5
    val appElapsedTime = p2ElapsedTime + p3ElapsedTime

    val muid1 = UUID.randomUUID()
    val muid2 = UUID.randomUUID()
    val muid3 = UUID.randomUUID()

    val oldP1ElapsedTime1 = (p1ElapsedTime1 / 2).toLong
    val oldP1ElapsedTime2 = (p1ElapsedTime1 / 2).toLong
    val oldP2ElapsedTime = (p2ElapsedTime / 2).toLong
    val oldP3ElapsedTime = (p3ElapsedTime / 2).toLong
    val oldAppElapsedTime = oldP2ElapsedTime + oldP3ElapsedTime
    val oldGlobalElapsedTime1 = (globalElapsedTime1 / 2).toLong
    val oldGlobalElapsedTime2 = (globalElapsedTime2 / 2).toLong
    val oldGlobalElapsedTime3 = (globalElapsedTime3 / 2).toLong

    val processRatio1 = TargetUsageRatio((p1ElapsedTime1 - oldP1ElapsedTime1).toDouble / (globalElapsedTime1 - oldGlobalElapsedTime1))

    val processRatio2 = TargetUsageRatio((p1ElapsedTime2 - oldP1ElapsedTime2).toDouble / (globalElapsedTime2 - oldGlobalElapsedTime2))
    val appRatio = TargetUsageRatio((appElapsedTime - oldAppElapsedTime).toDouble / (globalElapsedTime2 - oldGlobalElapsedTime2))

    val tickMock = ClockTick("test", 25.milliseconds)

    val cpuSensor = TestActorRef(Props(classOf[CpuSensor], eventBus, new OSHelper {
      private var targetTimes = Map[Target, List[Long]](
        Process(1) -> List(oldP1ElapsedTime1, oldP1ElapsedTime2, p1ElapsedTime1, p1ElapsedTime2),
        Process(2) -> List(oldP2ElapsedTime, p2ElapsedTime),
        Process(3) -> List(oldP3ElapsedTime, p3ElapsedTime)
      )

      private var globalTimes = List[Long](
        oldGlobalElapsedTime1, oldGlobalElapsedTime2, oldGlobalElapsedTime2, oldGlobalElapsedTime3,
        globalElapsedTime1, globalElapsedTime2, globalElapsedTime2, globalElapsedTime3
      )

      def getProcesses(application: Application): List[Process] = List(Process(2), Process(3))

      def getThreads(process: Process): List[Thread] = List()

      def getProcessCpuTime(process: Process): Option[Long] = {
        targetTimes.getOrElse(process, List()) match {
          case times if times.length > 0 => {
            targetTimes += process -> times.tail
            Some(times.head)
          }
          case _ => None
        }
      }

      def getGlobalCpuTime: Option[Long] = {
        globalTimes.headOption match {
          case time: Some[Long] => {
            globalTimes = globalTimes.tail
            time
          }
          case _ => None
        }
      }

      def getTimeInStates: TimeInStates = TimeInStates(Map())
    }), "simple-CpuSensor1")(system)

    subscribeSimpleUsageReport(eventBus)(testActor)

    publishMonitorTick(muid1, 1, tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid1); ur.target should equal(Process(1)); ur.targetRatio should equal(TargetUsageRatio(0.0))
    }
    publishMonitorTick(muid2, 1, tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid2); ur.target should equal(Process(1)); ur.targetRatio should equal(TargetUsageRatio(0.0))
    }
    publishMonitorTick(muid2, "app", tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid2); ur.target should equal(Application("app")); ur.targetRatio should equal(TargetUsageRatio(0.0))
    }
    publishMonitorTick(muid3, All, tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid3); ur.target should equal(All); ur.targetRatio should equal(TargetUsageRatio(0.0))
    }

    publishMonitorTick(muid1, 1, tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid1); ur.target should equal(Process(1)); ur.targetRatio should equal(processRatio1)
    }
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache(CacheKey(muid1, 1))(0, 0) match {
      case times => times should equal(p1ElapsedTime1, globalElapsedTime1)
    }
    publishMonitorTick(muid2, 1, tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid2); ur.target should equal(Process(1)); ur.targetRatio should equal(processRatio2)
    }
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache(CacheKey(muid2, 1))(0, 0) match {
      case times => times should equal(p1ElapsedTime2, globalElapsedTime2)
    }
    publishMonitorTick(muid2, "app", tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid2); ur.target should equal(Application("app")); ur.targetRatio should equal(appRatio)
    }
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache(CacheKey(muid2, "app"))(0, 0) match {
      case times => times should equal(appElapsedTime, globalElapsedTime2)
    }
    publishMonitorTick(muid3, All, tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid3); ur.target should equal(All); ur.targetRatio should equal(TargetUsageRatio(1.0))
    }
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache(CacheKey(muid3, All))(0, 0) match {
      case times => times should equal(globalElapsedTime3, globalElapsedTime3)
    }

    gracefulStop(cpuSensor, 1.seconds)
  }

  it should "handle correctly the time differences for computing the TargetUsageRatio" in {
    import akka.pattern.gracefulStop
    import org.powerapi.core.{OSHelper, Thread, TimeInStates}
    import org.powerapi.core.target.{Application, intToProcess, Process, Target, TargetUsageRatio}
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.core.MonitorChannel.publishMonitorTick
    import org.powerapi.module.CacheKey
    import org.powerapi.module.cpu.UsageMetricsChannel.UsageReport
    import org.powerapi.module.cpu.UsageMetricsChannel.subscribeSimpleUsageReport

    val eventBus = new MessageBus

    val globalElapsedTime = (43171 + 1 + 24917 + 25883594 + 1160 + 19 + 1477 + 0).toLong
    val p1ElapsedTime = (33 + 2).toLong

    val cpuSensor = TestActorRef(Props(classOf[CpuSensor], eventBus, new OSHelper {
      private var targetTimes = Map[Target, List[Long]](Process(1) -> List(p1ElapsedTime + 10, p1ElapsedTime))
      private var globalTimes = List[Long](globalElapsedTime, globalElapsedTime)

      def getProcesses(application: Application): List[Process] = List()

      def getThreads(process: Process): List[Thread] = List()

      def getProcessCpuTime(process: Process): Option[Long] = {
        targetTimes.getOrElse(process, List()) match {
          case times if times.length > 0 => {
            targetTimes += process -> times.tail
            Some(times.head)
          }
          case _ => None
        }
      }

      def getGlobalCpuTime: Option[Long] = {
        globalTimes.headOption match {
          case time: Some[Long] => {
            globalTimes = globalTimes.tail
            time
          }
          case _ => None
        }
      }

      def getTimeInStates: TimeInStates = TimeInStates(Map())
    }), "simple-CpuSensor2")(system)

    val oldP1ElapsedTime = p1ElapsedTime / 2
    val oldGlobalElapsedTime = globalElapsedTime / 2

    val muid = UUID.randomUUID()
    val tickMock = ClockTick("test", 25.milliseconds)

    subscribeSimpleUsageReport(eventBus)(testActor)

    publishMonitorTick(muid, 1, tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid); ur.target should equal(Process(1)); ur.targetRatio should equal(TargetUsageRatio(0.0))
    }
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache(CacheKey(muid, 1))(0, 0) match {
      case times => times should equal(p1ElapsedTime + 10, globalElapsedTime)
    }

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache(CacheKey(muid, 1)) = (p1ElapsedTime , globalElapsedTime + 10)
    publishMonitorTick(muid, 1, tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid); ur.target should equal(Process(1)); ur.targetRatio should equal(TargetUsageRatio(0.0))
    }
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache(CacheKey(muid, 1))(0, 0) match {
      case times => times should equal(p1ElapsedTime , globalElapsedTime + 10)
    }

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache(CacheKey(muid, 1)) = (globalElapsedTime, p1ElapsedTime)
    publishMonitorTick(muid, 1, tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid); ur.target should equal(Process(1)); ur.targetRatio should equal(TargetUsageRatio(0.0))
    }
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].cpuTimesCache(CacheKey(muid, 1))(0, 0) match {
      case times => times should equal(globalElapsedTime, p1ElapsedTime)
    }

    gracefulStop(cpuSensor, 1.seconds)
  }
}
