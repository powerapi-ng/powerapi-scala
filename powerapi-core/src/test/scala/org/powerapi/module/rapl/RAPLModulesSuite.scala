/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2015 Inria, University of Lille 1.
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
package org.powerapi.module.rapl

import java.util.UUID
import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.{GlobalCpuTime, TimeInStates, Thread, OSHelper}
import org.powerapi.core.target.{Application, TargetUsageRatio, Process}
import scala.concurrent.duration.DurationInt

class RAPLModulesSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("RAPLModulesSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "The RAPLModule class" should "create the underlying classes (sensors/formulae)" in {
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
    val raplHelper = new RAPLHelper

    val module = new RAPLModule(osHelper, raplHelper)

    module.underlyingSensorsClasses.size should equal(1)
    module.underlyingSensorsClasses(0)._1 should equal(classOf[RAPLSensor])
    module.underlyingSensorsClasses(0)._2.size should equal(2)
    module.underlyingSensorsClasses(0)._2(0) should equal(osHelper)
    module.underlyingSensorsClasses(0)._2(1) should equal(raplHelper)

    module.underlyingFormulaeClasses.size should equal(1)  }
}
