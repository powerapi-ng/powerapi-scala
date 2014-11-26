/**
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

 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */
package org.powerapi.module.procfs.simple

import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.{MessageBus, OSHelper}

import scala.concurrent.duration.DurationInt

trait SimpleCpuSensorConfigurationMock extends SensorConfiguration {
  val basepath = getClass.getResource("/").getPath

  override lazy val globalStatPath = s"$basepath/proc/stat"
  override lazy val processStatPath = s"$basepath/proc/%?pid/stat"
}

class SimpleCpuSensorMock(messageBus: MessageBus, osHelper: OSHelper)
  extends CpuSensor(messageBus, osHelper)
  with SimpleCpuSensorConfigurationMock

class OSHelperMock extends OSHelper {
  import org.powerapi.core.{Application, Process, Thread}

  def getProcesses(application: Application): List[Process] = List(Process(2), Process(3))

  def getThreads(process: Process): List[Thread] = List()
}

class SimpleCpuSensorSuite(system: ActorSystem) extends UnitTest(system) {
  import org.powerapi.core.ClockChannel.ClockTick
  import org.powerapi.core.MonitorChannel.MonitorTick
  import org.powerapi.core.{All, Application, Process}
  import org.powerapi.module.procfs.ProcMetricsChannel.{CacheKey, UsageReport, TargetUsageRatio}

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("SimpleCpuSensorSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val eventBus = new MessageBus

  val globalElapsedTime = 43171 + 1 + 24917 + 25883594 + 1160 + 19 + 1477 + 0
  val p1ElapsedTime = 33 + 2
  val p2ElapsedTime = 10 + 5
  val p3ElapsedTime = 3 + 5
  val appElapsedTime = p2ElapsedTime + p3ElapsedTime

  val cpuSensor = TestActorRef(Props(classOf[SimpleCpuSensorMock], eventBus, new OSHelperMock()), "simple-CpuSensor")(system)

  "A simple CpuSensor" should "read global elapsed time from a given dedicated system file" in {
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetRatio.globalElapsedTime should equal(Some(globalElapsedTime))
  }

  it should "read process elapsed time from a given dedicated system file" in {
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetRatio.processElapsedTime(Process(1)) should equal(Some(p1ElapsedTime))
  }

  it should "refresh its cache after each processed message" in {
    val muid = UUID.randomUUID()
    val processTarget = Process(1)

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetRatio.cache shouldBe empty
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetRatio.handleMonitorTick(MonitorTick("test", muid, processTarget, ClockTick("test", 25.milliseconds)))
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetRatio.cache should equal(
      Map(CacheKey(muid, processTarget) -> (p1ElapsedTime, globalElapsedTime))
    )
  }

  it should "handle a Process target or an Application target" in {
    val oldP1ElapsedTime = p1ElapsedTime / 2
    val oldAppElapsedTime = appElapsedTime / 2
    val oldGlobalElapsedTime = globalElapsedTime / 2

    val muid = UUID.randomUUID()
    val processTarget = Process(1)
    val appTarget = Application("app")

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetRatio.refreshCache(CacheKey(muid, processTarget), (oldP1ElapsedTime, oldGlobalElapsedTime))
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetRatio.refreshCache(CacheKey(muid, appTarget), (oldAppElapsedTime, oldGlobalElapsedTime))

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetRatio.handleMonitorTick(MonitorTick("test", muid, processTarget, ClockTick("test", 25.milliseconds))) should equal(
      TargetUsageRatio((p1ElapsedTime - oldP1ElapsedTime).toDouble / (globalElapsedTime - oldGlobalElapsedTime))
    )
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetRatio.handleMonitorTick(MonitorTick("test", muid, appTarget, ClockTick("test", 25.milliseconds))) should equal(
      TargetUsageRatio((appElapsedTime - oldAppElapsedTime).toDouble / (globalElapsedTime - oldGlobalElapsedTime))
    )
  }

  it should "not handle an All target" in {
    val muid = UUID.randomUUID()

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetRatio.handleMonitorTick(MonitorTick("test", muid, All, ClockTick("test", 25.milliseconds))) should equal(
      TargetUsageRatio(0)
    )
  }

  it should "process a MonitorTicks message and then publish a UsageReport" in {
    import org.powerapi.core.MonitorChannel.publishMonitorTick
    import org.powerapi.module.procfs.ProcMetricsChannel.subscribeSimpleUsageReport

    val oldP1ElapsedTime = p1ElapsedTime / 2
    val oldAppElapsedTime = appElapsedTime / 2
    val oldGlobalElapsedTime = globalElapsedTime / 2

    val muid1 = UUID.randomUUID()
    val muid2 = UUID.randomUUID()
    val tickMock = ClockTick("test", 25.milliseconds)

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetRatio.refreshCache(CacheKey(muid1, Process(1)), (oldP1ElapsedTime, oldGlobalElapsedTime))
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetRatio.refreshCache(CacheKey(muid2, Process(1)), (oldP1ElapsedTime, oldGlobalElapsedTime))
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetRatio.refreshCache(CacheKey(muid2, Application("app")), (oldAppElapsedTime, oldGlobalElapsedTime))

    val processRatio = TargetUsageRatio((p1ElapsedTime - oldP1ElapsedTime).toDouble / (globalElapsedTime - oldGlobalElapsedTime))
    val appRatio = TargetUsageRatio((appElapsedTime - oldAppElapsedTime).toDouble / (globalElapsedTime - oldGlobalElapsedTime))

    subscribeSimpleUsageReport(eventBus)(testActor)

    publishMonitorTick(muid1, Process(1), tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case UsageReport(_, id, Process(1), processr, _, _) if muid1 == id && processRatio == processr => assert(true)
      case _ => assert(false)
    }
    publishMonitorTick(muid2, Process(1), tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case UsageReport(_, id, Process(1), processr, _, _) if muid2 == id && processRatio == processr => assert(true)
      case _ => assert(false)
    }
    publishMonitorTick(muid2, Application("app"), tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case UsageReport(_, id, Application("app"), appr, _, _) if id == muid2 && appRatio == appr => assert(true)
      case _ => assert(false)
    }
  }
}
