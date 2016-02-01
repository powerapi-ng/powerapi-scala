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
package org.powerapi.module.extPMeter

import java.util.UUID
import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.{ExternalPMeter, MessageBus, GlobalCpuTime, TimeInStates, OSHelper, Thread}
import org.powerapi.core.target.{Application, TargetUsageRatio, Process}
import org.powerapi.core.power._
import scala.concurrent.duration.DurationInt

class ExtPMeterModulesSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("ExtPMeterModulesSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "The ExtPMeterModule class" should "create the underlying classes (sensors/formulae)" in {
    val osHelper = new OSHelper {
      override def getCPUFrequencies: Set[Long] = Set()
      override def getThreads(process: Process): Set[Thread] = Set()
      override def getTimeInStates: TimeInStates = TimeInStates(Map())
      override def getGlobalCpuPercent(muid: UUID): TargetUsageRatio = TargetUsageRatio(0.0)
      override def getProcessCpuPercent(muid: UUID, process: Process): TargetUsageRatio = TargetUsageRatio(0.0)
      override def getProcessCpuTime(process: Process): Option[Long] = None
      override def getGlobalCpuTime: GlobalCpuTime = GlobalCpuTime(0, 0)
      override def getProcesses(application: Application): Set[Process] = Set()
    }
    
    val mockPMeter = new ExternalPMeter {
      def init(bus: MessageBus): Unit = {}
      def start(): Unit = {}
      def stop(): Unit = {}
    }

    val module = new ExtPMeterModule(osHelper, mockPMeter, 1.W) {
      eventBus = Some(new MessageBus)
    }
    module.underlyingSensorsClasses.size should equal(1)
    module.underlyingSensorsClasses(0)._1 should equal(classOf[ExtPMeterSensor])
    module.underlyingSensorsClasses(0)._2.size should equal(1)
    module.underlyingSensorsClasses(0)._2(0).getClass should equal(mockPMeter.getClass)

    module.underlyingFormulaeClasses.size should equal(1)
    module.underlyingFormulaeClasses(0)._1 should equal(classOf[ExtPMeterFormula])
    module.underlyingFormulaeClasses(0)._2.size should equal(2)
    module.underlyingFormulaeClasses(0)._2(0) should equal(osHelper)
    module.underlyingFormulaeClasses(0)._2(1) should equal(1.W)
  }
}
