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
package org.powerapi.module.powerspy

import java.util.UUID

import akka.actor.{Actor, Props, ActorSystem}
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.{OSHelper, MessageBus}
import scala.concurrent.duration.DurationInt

class PSpyDataListener(eventBus: MessageBus, muid: UUID) extends Actor {
  import PSpyMetricsChannel.{PSpyDataReport, subscribePSpyAllDataReport, subscribePSpyRatioDataReport}

  override def preStart(): Unit = {
    subscribePSpyAllDataReport(eventBus)(self)
    subscribePSpyRatioDataReport(eventBus)(self)
  }

  def receive = {
    case msg: PSpyDataReport => println(msg)
  }
}

class PowerSpySensorMock(eventBus: MessageBus, osHelper: OSHelper, timeout: Timeout)
  extends PowerSpySensor(eventBus, osHelper, timeout) {

  override lazy val sppUrl = "btspp://000BCE071E9B:1;authenticate=false;encrypt=false;master=false"
  override lazy val version = PowerSpyVersion.POWERSPY_V1
}

class OSHelperMock extends OSHelper {
  import org.powerapi.core.{Application, Process, Thread, TimeInStates}

  def getProcesses(application: Application): List[Process] = {
    application match {
      case Application("app") => List(Process(2), Process(3))
    }
  }

  def getThreads(process: Process): List[Thread] = List()

  def getProcessCpuTime(process: Process): Option[Long] = {
    process match {
      case Process(1) => Some(33 + 2)
      case Process(2) => Some(10 + 5)
      case Process(3) => Some(3 + 5)
      case _ => None
    }
  }

  def getGlobalCpuTime: Option[Long] = Some(43171 + 1 + 24917 + 25883594 + 1160 + 19 + 1477 + 0)

  def getTimeInStates: TimeInStates = TimeInStates(Map())
}

class PowerSpySensorSuite(system: ActorSystem) extends UnitTest(system) {
  import PSpyMetricsChannel.PSpyChildMessage

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("PowerSpySensorSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "A PSpyChildMessage" can "be summed with another one" in {
    PSpyChildMessage(3.0, 4.0f, 1.0f) + PSpyChildMessage(4.0, 5.0f, 7.0f) should equal(PSpyChildMessage(7.0, 9.0f, 8.0f))
  }

  it can "be divided with another one" in {
    PSpyChildMessage(3.0, 6.0f, 9.0f) / 2 should equal(Some(PSpyChildMessage(3.0 / 2, 6.0f / 2, 9.0f / 2)))
    PSpyChildMessage(3.0, 3.0f, 3.0f) / 0 should equal(None)
  }

  "An average of a PSpyChildMessage messages" can "be computed" in {
    PSpyChildMessage.avg(List(PSpyChildMessage(3.0, 6.0f, 9.0f), PSpyChildMessage(1.0, 4.0F, 8.0f))) should equal(
      Some(PSpyChildMessage(4.0 / 2, 10.0f / 2, 17.0f / 2))
    )
    PSpyChildMessage.avg(List()) should equal(None)
  }

  "A PowerSpySensor" should "produce a PSpyRatioDataReport/PSpyAllDataReport message if it collected PowerSpy data" in {
    import akka.pattern.gracefulStop
    import org.powerapi.core.{Application, All, Process, TargetUsageRatio}
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.core.MonitorChannel.publishMonitorTick
    import org.powerapi.module.CacheKey
    import org.powerapi.module.powerspy.PSpyMetricsChannel.{PSpyAllDataReport, PSpyRatioDataReport, subscribePSpyAllDataReport, subscribePSpyRatioDataReport}

    val eventBus = new MessageBus
    val pspySensor = TestActorRef(Props(classOf[PowerSpySensorMock], eventBus, new OSHelperMock, Timeout(20.seconds)), "pspySensor")(system)
    val muid1 = UUID.randomUUID()
    val muid2 = UUID.randomUUID()
    val tickMock = ClockTick("test", 25.milliseconds)

    val globalElapsedTime = 43171 + 1 + 24917 + 25883594 + 1160 + 19 + 1477 + 0
    val p1ElapsedTime = 33 + 2
    val p2ElapsedTime = 10 + 5
    val p3ElapsedTime = 3 + 5
    val appElapsedTime = p2ElapsedTime + p3ElapsedTime

    val oldP1ElapsedTime = p1ElapsedTime / 2
    val oldAppElapsedTime = appElapsedTime / 2
    val oldGlobalElapsedTime = globalElapsedTime / 2

    val processRatio = TargetUsageRatio((p1ElapsedTime - oldP1ElapsedTime).toDouble / (globalElapsedTime - oldGlobalElapsedTime))
    val appRatio = TargetUsageRatio((appElapsedTime - oldAppElapsedTime).toDouble / (globalElapsedTime - oldGlobalElapsedTime))

    val rms = 3.0
    val uScale = 6.0f
    val iScale = 9.0f

    subscribePSpyRatioDataReport(eventBus)(testActor)
    subscribePSpyAllDataReport(eventBus)(testActor)

    pspySensor.underlyingActor.asInstanceOf[PowerSpySensor].cpuTimesCache.update(CacheKey(muid1, Process(1)), (oldP1ElapsedTime, oldGlobalElapsedTime))
    pspySensor.underlyingActor.asInstanceOf[PowerSpySensor].cpuTimesCache.update(CacheKey(muid2, Process(1)), (oldP1ElapsedTime, oldGlobalElapsedTime))
    pspySensor.underlyingActor.asInstanceOf[PowerSpySensor].cpuTimesCache.update(CacheKey(muid2, Application("app")), (oldAppElapsedTime, oldGlobalElapsedTime))

    pspySensor ! PSpyChildMessage(rms, uScale, iScale)
    publishMonitorTick(muid1, Process(1), tickMock)(eventBus)
    expectMsgClass(classOf[PSpyRatioDataReport]) match {
      case pr: PSpyRatioDataReport => {
        pr.muid should equal(muid1)
        pr.target should equal(Process(1)); pr.targetRatio should equal(processRatio)
        pr.rms should equal(rms); pr.uScale should equal(uScale); pr.iScale should equal(iScale)
      }
    }
    pspySensor.underlyingActor.asInstanceOf[PowerSpySensor].cpuTimesCache.getOrElse(CacheKey(muid1, Process(1)), (0, 0)) match {
      case times => times should equal(p1ElapsedTime, globalElapsedTime)
    }

    pspySensor ! PSpyChildMessage(rms, uScale, iScale)
    publishMonitorTick(muid2, Process(1), tickMock)(eventBus)
    expectMsgClass(classOf[PSpyRatioDataReport]) match {
      case pr: PSpyRatioDataReport => {
        pr.muid should equal(muid2)
        pr.target should equal(Process(1)); pr.targetRatio should equal(processRatio)
        pr.rms should equal(rms); pr.uScale should equal(uScale); pr.iScale should equal(iScale)
      }
    }
    pspySensor.underlyingActor.asInstanceOf[PowerSpySensor].cpuTimesCache.getOrElse(CacheKey(muid2, Process(1)), (0, 0)) match {
      case times => times should equal(p1ElapsedTime, globalElapsedTime)
    }

    pspySensor ! PSpyChildMessage(rms, uScale, iScale)
    publishMonitorTick(muid2, Application("app"), tickMock)(eventBus)
    expectMsgClass(classOf[PSpyRatioDataReport]) match {
      case pr: PSpyRatioDataReport => {
        pr.muid should equal(muid2)
        pr.target should equal(Application("app")); pr.targetRatio should equal(appRatio)
        pr.rms should equal(rms); pr.uScale should equal(uScale); pr.iScale should equal(iScale)
      }
    }
    pspySensor.underlyingActor.asInstanceOf[PowerSpySensor].cpuTimesCache.getOrElse(CacheKey(muid2, Application("app")), (0, 0)) match {
      case times => times should equal(appElapsedTime, globalElapsedTime)
    }

    pspySensor ! PSpyChildMessage(rms, uScale, iScale)
    publishMonitorTick(muid1, All, tickMock)(eventBus)
    expectMsgClass(classOf[PSpyAllDataReport]) match {
      case pr: PSpyAllDataReport => {
        pr.muid should equal(muid1)
        pr.target should equal(All)
        pr.rms should equal(rms); pr.uScale should equal(uScale); pr.iScale should equal(iScale)
      }
    }
    pspySensor.underlyingActor.asInstanceOf[PowerSpySensor].cpuTimesCache.getOrElse(CacheKey(muid2, Application("app")), (0, 0)) match {
      case times => times should equal(appElapsedTime, globalElapsedTime)
    }

    gracefulStop(pspySensor, 20.seconds)
  }

  it should "handle correctly the time differences for computing the TargetUsageRatio" in {
    import akka.pattern.gracefulStop
    import org.powerapi.core.{Application, All, Process, TargetUsageRatio}
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.core.MonitorChannel.publishMonitorTick
    import org.powerapi.module.CacheKey
    import org.powerapi.module.powerspy.PSpyMetricsChannel.{PSpyAllDataReport, PSpyRatioDataReport, subscribePSpyAllDataReport, subscribePSpyRatioDataReport}

    val eventBus = new MessageBus
    val pspySensor = TestActorRef(Props(classOf[PowerSpySensorMock], eventBus, new OSHelperMock, Timeout(20.seconds)), "pspySensor")(system)
    val muid = UUID.randomUUID()
    val tickMock = ClockTick("test", 25.milliseconds)

    val globalElapsedTime = 43171 + 1 + 24917 + 25883594 + 1160 + 19 + 1477 + 0
    val p1ElapsedTime = 33 + 2

    val rms = 3.0
    val uScale = 6.0f
    val iScale = 9.0f

    subscribePSpyRatioDataReport(eventBus)(testActor)

    pspySensor ! PSpyChildMessage(rms, uScale, iScale)
    pspySensor.underlyingActor.asInstanceOf[PowerSpySensor].cpuTimesCache.update(CacheKey(muid, Process(1)), (p1ElapsedTime + 10, globalElapsedTime))
    publishMonitorTick(muid, Process(1), tickMock)(eventBus)
    expectMsgClass(classOf[PSpyRatioDataReport]) match {
      case pr: PSpyRatioDataReport => {
        pr.muid should equal(muid)
        pr.target should equal(Process(1)); pr.targetRatio should equal(TargetUsageRatio(0.0))
        pr.rms should equal(rms); pr.uScale should equal(uScale); pr.iScale should equal(iScale)
      }
    }
    pspySensor.underlyingActor.asInstanceOf[PowerSpySensor].cpuTimesCache.getOrElse(CacheKey(muid, Process(1)), (0, 0)) match {
      case times => times should equal(p1ElapsedTime + 10, globalElapsedTime)
    }

    pspySensor ! PSpyChildMessage(rms, uScale, iScale)
    pspySensor.underlyingActor.asInstanceOf[PowerSpySensor].cpuTimesCache.update(CacheKey(muid, Process(1)), (p1ElapsedTime , globalElapsedTime + 10))
    publishMonitorTick(muid, Process(1), tickMock)(eventBus)
    expectMsgClass(classOf[PSpyRatioDataReport]) match {
      case pr: PSpyRatioDataReport => {
        pr.muid should equal(muid)
        pr.target should equal(Process(1)); pr.targetRatio should equal(TargetUsageRatio(0.0))
        pr.rms should equal(rms); pr.uScale should equal(uScale); pr.iScale should equal(iScale)
      }
    }
    pspySensor.underlyingActor.asInstanceOf[PowerSpySensor].cpuTimesCache.getOrElse(CacheKey(muid, Process(1)), (0, 0)) match {
      case times => times should equal(p1ElapsedTime , globalElapsedTime + 10)
    }

    pspySensor ! PSpyChildMessage(rms, uScale, iScale)
    pspySensor.underlyingActor.asInstanceOf[PowerSpySensor].cpuTimesCache.update(CacheKey(muid, Process(1)), (globalElapsedTime, p1ElapsedTime))
    publishMonitorTick(muid, Process(1), tickMock)(eventBus)
    expectMsgClass(classOf[PSpyRatioDataReport]) match {
      case pr: PSpyRatioDataReport => {
        pr.muid should equal(muid)
        pr.target should equal(Process(1)); pr.targetRatio should equal(TargetUsageRatio(0.0))
        pr.rms should equal(rms); pr.uScale should equal(uScale); pr.iScale should equal(iScale)
      }
    }
    pspySensor.underlyingActor.asInstanceOf[PowerSpySensor].cpuTimesCache.getOrElse(CacheKey(muid, Process(1)), (0, 0)) match {
      case times => times should equal(globalElapsedTime, p1ElapsedTime)
    }

    gracefulStop(pspySensor, 20.seconds)
  }

  it should "open the connection with the power meter, collect the data and then produce PSpyRatioDataReport messages when the target is Process/Application" ignore {
    import org.powerapi.core.{Application, Clocks, Monitors}
    import org.powerapi.core.MonitorChannel.startMonitor
    import akka.pattern.gracefulStop

    val eventBus = new MessageBus
    val muid = UUID.randomUUID()
    TestActorRef(Props(classOf[Clocks], eventBus), "clocks")(system)
    TestActorRef(Props(classOf[Monitors], eventBus), "monitors")(system)
    val pspySensor = TestActorRef(Props(classOf[PowerSpySensorMock], eventBus, new OSHelperMock, Timeout(20.seconds)), "pspySensor")(system)
    TestActorRef(Props(classOf[PSpyDataListener], eventBus, muid), "pspyListener")(system)

    startMonitor(muid, 1.seconds, List(Application("app")))(eventBus)
    Thread.sleep(20.seconds.toMillis)

    gracefulStop(pspySensor, 20.seconds)
  }

  it should "open the connection with the power meter, collect the data and then produce PSpyAllDataReport messages when the target is All" ignore {
    import org.powerapi.core.{All, Clocks, Monitors}
    import org.powerapi.core.MonitorChannel.startMonitor
    import akka.pattern.gracefulStop

    val eventBus = new MessageBus
    val muid = UUID.randomUUID()
    TestActorRef(Props(classOf[Clocks], eventBus), "clocks")(system)
    TestActorRef(Props(classOf[Monitors], eventBus), "monitors")(system)
    val pspySensor = TestActorRef(Props(classOf[PowerSpySensorMock], eventBus, new OSHelperMock, Timeout(20.seconds)), "pspySensor")(system)
    TestActorRef(Props(classOf[PSpyDataListener], eventBus, muid), "pspyListener")(system)

    startMonitor(muid, 1.seconds, List(All))(eventBus)
    Thread.sleep(20.seconds.toMillis)

    gracefulStop(pspySensor, 20.seconds)
  }
}
