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
import org.powerapi.core.MonitorChannel.{MonitorSubscription, MonitorTicks}
import org.powerapi.core._
import org.powerapi.sensors.procfs.cpu.CpuSensorChannel.{CacheKey, CpuSensorReport, TargetRatio, subscribeCpuProcSensor}

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
    subscribeCpuProcSensor(eventBus)(self)
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
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetRatio.globalElapsedTime should equal(Some(globalElapsedTime))
  }

  it should "read process elapsed time from a given dedicated system file" in {
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetRatio.processElapsedTime(Process(1)) should equal(Some(p1ElapsedTime))
  }

  it should "refresh its cache after each processed message" in {
    val muid = UUID.randomUUID()
    val processTarget = Process(1)

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetRatio.cache shouldBe empty
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetRatio.handleTarget(muid, processTarget)
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

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetRatio.handleTarget(muid, processTarget) should equal(
      TargetRatio((p1ElapsedTime - oldP1ElapsedTime).toDouble / (globalElapsedTime - oldGlobalElapsedTime))
    )
    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetRatio.handleTarget(muid, appTarget) should equal(
      TargetRatio((appElapsedTime - oldAppElapsedTime).toDouble / (globalElapsedTime - oldGlobalElapsedTime))
    )
  }

  it should "not handle an All target" in {
    val muid = UUID.randomUUID()

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].targetRatio.handleTarget(muid, All) should equal(
      TargetRatio(0)
    )
  }

  it should "process a MonitorTargets message and then publish a CpuSensorReport" in {
    TestActorRef(Props(classOf[MockSubscriber], eventBus, testActor), "subscriber")(system)
    val monitorTargets = MonitorTicks("test", MonitorSubscription(UUID.randomUUID(), 25.milliseconds, List(Process(1), Application("app"))), System.currentTimeMillis)

    cpuSensor.underlyingActor.asInstanceOf[CpuSensor].sense(monitorTargets)
    expectMsgClass(classOf[CpuSensorReport])
    expectMsgClass(classOf[CpuSensorReport])
  }
}
