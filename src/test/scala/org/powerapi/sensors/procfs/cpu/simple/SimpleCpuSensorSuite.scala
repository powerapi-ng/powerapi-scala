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

package org.powerapi.sensors.procfs.cpu.simple

import java.util.UUID

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.MonitorChannel.MonitorTarget
import org.powerapi.core._
import org.powerapi.sensors.procfs.cpu.CpuSensorChannel.{CacheKey, CpuSensorReport, TargetPercent, subscribeCpuSensor}

import scala.concurrent.duration.DurationInt

trait SimpleCpuSensorConfigurationMock extends Configuration {
  val basepath = getClass.getResource("/").getPath

  override lazy val globalStatPath = s"$basepath/proc/stat"
  override lazy val processStatPath = s"$basepath/proc/%?pid/stat"
}

class SimpleCpuSensorMock(messageBus: MessageBus, osHelper: OSHelper)
  extends CpuSensor(messageBus, osHelper)
  with SimpleCpuSensorConfigurationMock

class OSHelperMock extends OSHelper {
  def getProcesses(application: Application): List[Process] = List(Process(2), Process(3))

  def getThreads(process: Process): List[Thread] = List()
}

class MockSubscriber(eventBus: MessageBus, actorRef: ActorRef) extends Actor {
  override def preStart() = {
    subscribeCpuSensor(eventBus)(self)
  }

  def receive = {
    case msg: CpuSensorReport => actorRef ! msg
  }
}

/**
 * SimpleCpuSensorSuite
 *
 * @author abourdon
 * @author mcolmant
 */
class SimpleCpuSensorSuite(system: ActorSystem) extends UnitTest(system) {

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
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetPercent.globalElapsedTime should equal(globalElapsedTime)
  }

  it should "read process elapsed time from a given dedicated system file" in {
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetPercent.processElapsedTime(Process(1)) should equal(p1ElapsedTime)
  }

  it should "refresh its cache after each processed message" in {
    val monitorTarget = MonitorTarget("test", UUID.randomUUID(), Process(1), 25.milliseconds, System.currentTimeMillis)
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetPercent.cache shouldBe empty
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetPercent.handleMonitorTarget(monitorTarget)
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetPercent.cache should equal(
      Map(CacheKey(monitorTarget.muid, monitorTarget.target) -> (p1ElapsedTime, globalElapsedTime))
    )
  }

  it should "handle a Process target or an Application target" in {
    val oldP1ElapsedTime = p1ElapsedTime / 2
    val oldAppElapsedTime = appElapsedTime / 2
    val oldGlobalElapsedTime = globalElapsedTime / 2

    val processTarget = MonitorTarget("test", UUID.randomUUID(), Process(1), 25.milliseconds, System.currentTimeMillis)
    val appTarget = MonitorTarget("test", UUID.randomUUID(), Application("app"), 50.milliseconds, System.currentTimeMillis())

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetPercent.refreshCache(CacheKey(processTarget.muid, Process(1)), (oldP1ElapsedTime, oldGlobalElapsedTime))
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetPercent.refreshCache(CacheKey(appTarget.muid, Application("app")), (oldAppElapsedTime, oldGlobalElapsedTime))

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetPercent.handleMonitorTarget(processTarget) should equal(
      TargetPercent((p1ElapsedTime - oldP1ElapsedTime).toDouble / (globalElapsedTime - oldGlobalElapsedTime))
    )
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetPercent.handleMonitorTarget(appTarget) should equal(
      TargetPercent((appElapsedTime - oldAppElapsedTime).toDouble / (globalElapsedTime - oldGlobalElapsedTime))
    )
  }

  it should "not handle a App target" in {
    val allTarget = MonitorTarget("test", UUID.randomUUID(), All, 25.milliseconds, System.currentTimeMillis)

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetPercent.handleMonitorTarget(allTarget) should equal(
      TargetPercent(0)
    )
  }

  it should "process a MonitorTarget message and then publish a CpuReport" in {
    TestActorRef(Props(classOf[MockSubscriber], eventBus, testActor), "subscriber")(system)

    val processTarget = MonitorTarget("test", UUID.randomUUID(), Process(1), 25.milliseconds, System.currentTimeMillis)
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].process(processTarget)
    expectMsgClass(classOf[CpuSensorReport])
  }
}
