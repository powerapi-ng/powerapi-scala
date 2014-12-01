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
package org.powerapi.module.procfs.dvfs

import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.{MessageBus, OSHelper}

import scala.concurrent.duration.DurationInt

class OSHelperMock extends OSHelper {
  import org.powerapi.core.{Application, Process, Target, TargetUsageRatio, Thread, TimeInStates}

  def getProcesses(application: Application): List[Process] = List(Process(2), Process(3))

  def getThreads(process: Process): List[Thread] = List()

  def getProcessCpuTime(process: Process): Option[Long] = {
    process match {
      case Process(1) => Some(33 + 2)
      case Process(2) => Some(10 + 5)
      case Process(3) => Some(3 + 5)
      case _ => None
    }
  }

  def getGlobalCpuTime(): Option[Long] = Some(43171 + 1 + 24917 + 25883594 + 1160 + 19 + 1477 + 0)

  def getTimeInStates(): TimeInStates = TimeInStates(Map(4000000l -> 16l, 3000000l -> 12l, 2000000l -> 8l, 1000000l -> 4l))
}

class DvfsCpuSensorSuite(system: ActorSystem) extends UnitTest(system) {
  import org.powerapi.core.ClockChannel.ClockTick
  import org.powerapi.core.MonitorChannel.MonitorTick
  import org.powerapi.core.{Application, Process}
  import org.powerapi.module.procfs.ProcMetricsChannel.{CacheKey, UsageReport}

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("DvfsCpuSensorSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val eventBus = new MessageBus

  val cpuSensor = TestActorRef(Props(classOf[CpuSensor], eventBus, new OSHelperMock()), "dvfs-CpuSensor")(system)

  "Frequencies' cache" should "be correctly updated during process phase" in {
    import org.powerapi.core.TimeInStates

    val muid = UUID.randomUUID()
    val processTarget = Process(1)
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].frequencies.cache should have size 0
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].frequencies.handleMonitorTick(MonitorTick("test", muid, processTarget, ClockTick("test", 25.milliseconds)))
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].frequencies.cache should equal(
      Map(CacheKey(muid, processTarget) -> TimeInStates(Map(4000000l -> 16l, 3000000l -> 12l, 2000000l -> 8l, 1000000l -> 4l)))
    )
  }

  "A dvfs CpuSensor" should "process a MonitorTicks message and then publish a UsageReport" in {
    import org.powerapi.core.MonitorChannel.publishMonitorTick
    import org.powerapi.core.TimeInStates
    import org.powerapi.module.procfs.ProcMetricsChannel.subscribeDvfsUsageReport

    val muid = UUID.randomUUID()
    val tickMock = ClockTick("test", 25.milliseconds)
    val timeInStates = TimeInStates(Map(4000000l -> 6l, 3000000l -> 2l, 2000000l -> 2l, 1000000l -> 2l))

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].frequencies.refreshCache(CacheKey(muid, Process(1)),
      TimeInStates(Map(4000000l -> 10l, 3000000l -> 10l, 2000000l -> 6l, 1000000l -> 2l))
    )
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].frequencies.refreshCache(CacheKey(muid, Application("app")),
      TimeInStates(Map(4000000l -> 10l, 3000000l -> 10l, 2000000l -> 6l, 1000000l -> 2l))
    )

    subscribeDvfsUsageReport(eventBus)(testActor)

    publishMonitorTick(muid, Process(1), tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case UsageReport(_, id, Process(1), _, times ,_) if muid == id && timeInStates == times => assert(true)
      case _ => assert(false)
    }
    publishMonitorTick(muid, Application("app"), tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case UsageReport(_, id, Application("app"), _, times ,_) if muid == id && timeInStates == times => assert(true)
      case _ => assert(false)
    }
  }
}
