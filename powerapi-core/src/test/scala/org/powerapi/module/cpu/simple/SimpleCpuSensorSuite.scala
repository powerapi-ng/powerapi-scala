/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2016 Inria, University of Lille 1.
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

import akka.actor.{ActorSystem, Props}
import akka.pattern.gracefulStop
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import java.util.UUID
import org.powerapi.UnitTest
import org.powerapi.core.MessageBus
import org.powerapi.core.{OSHelper, Thread, TimeInStates}
import org.powerapi.core.target.{All, Application, Container, intToProcess, stringToApplication, Process, Target, TargetUsageRatio}
import org.powerapi.core.ClockChannel.ClockTick
import org.powerapi.core.MonitorChannel.publishMonitorTick
import org.powerapi.module.cpu.UsageMetricsChannel.UsageReport
import org.powerapi.module.cpu.UsageMetricsChannel.subscribeSimpleUsageReport
import scala.concurrent.duration.DurationInt

class SimpleCpuSensorSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("SimpleCpuSensorSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "A simple CpuSensor" should "process a MonitorTick message and then publish a UsageReport" in {
    val eventBus = new MessageBus

    val muid1 = UUID.randomUUID()
    val muid2 = UUID.randomUUID()
    val muid3 = UUID.randomUUID()
    val muid4 = UUID.randomUUID()

    val processRatio1 = 0.05
    val processRatio2 = 0.23
    val appRatio = 0.44

    val allRatio = 0.87

    val tickMock = ClockTick("test", 25.milliseconds)

    val cpuSensor = TestActorRef(Props(classOf[CpuSensor], eventBus, new OSHelper {
      import org.powerapi.core.GlobalCpuTime

      private val targetUsage = Map[Target, Double](
        Process(1) -> processRatio1,
        Process(2) -> processRatio2,
        Process(3) -> appRatio
      )

      private val globalUsages = allRatio

      def getCPUFrequencies: Set[Long] = Set()

      def getProcesses(application: Application): Set[Process] = Set(Process(2), Process(3))
      
      override def getProcesses(container: Container): Set[Process] = Set(Process(1), Process(2))

      def getThreads(process: Process): Set[Thread] = Set()

      def getProcessCpuTime(process: Process): Option[Long] = None

      def getGlobalCpuTime: GlobalCpuTime = GlobalCpuTime(0, 0)
      
      def getProcessCpuPercent(muid: UUID, process: Process) = TargetUsageRatio(targetUsage.getOrElse(process, 0.0))
      
      def getGlobalCpuPercent(muid: UUID) = TargetUsageRatio(globalUsages)

      def getTimeInStates: TimeInStates = TimeInStates(Map())
    }), "simple-CpuSensor1")(system)

    subscribeSimpleUsageReport(eventBus)(testActor)

    publishMonitorTick(muid1, 1, tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid1); ur.target should equal(Process(1)); ur.targetRatio should equal(TargetUsageRatio(0.05))
    }
    publishMonitorTick(muid2, "app", tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid2); ur.target should equal(Application("app")); ur.targetRatio should equal(TargetUsageRatio(0.67))
    }
    publishMonitorTick(muid3, Container("ship"), tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid3); ur.target should equal(Container("ship")); ur.targetRatio should equal(TargetUsageRatio(0.28))
    }
    publishMonitorTick(muid4, All, tickMock)(eventBus)
    expectMsgClass(classOf[UsageReport]) match {
      case ur: UsageReport => ur.muid should equal(muid4); ur.target should equal(All); ur.targetRatio should equal(TargetUsageRatio(0.87))
    }

    gracefulStop(cpuSensor, 1.seconds)
  }
}
