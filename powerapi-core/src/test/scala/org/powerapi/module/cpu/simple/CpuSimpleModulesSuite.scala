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

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.{LinuxHelper, GlobalCpuTime, TimeInStates, OSHelper, SigarHelper, Thread}
import org.powerapi.core.target.{TargetUsageRatio, Process, Application}
import scala.concurrent.duration.DurationInt

class CpuSimpleModulesSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("CpuSimpleModulesSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "The CpuSimpleModule class" should "create the underlying classes (sensors/formulae)" in {
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

    val module = new CpuSimpleModule(osHelper, 10, 0.5)
    module.underlyingSensorsClasses.size should equal(1)
    module.underlyingSensorsClasses(0)._1 should equal(classOf[CpuSensor])
    module.underlyingSensorsClasses(0)._2.size should equal(1)
    module.underlyingSensorsClasses(0)._2(0) should equal(osHelper)

    module.underlyingFormulaeClasses.size should equal(1)
    module.underlyingFormulaeClasses(0)._1 should equal(classOf[CpuFormula])
    module.underlyingFormulaeClasses(0)._2.size should equal(2)
    module.underlyingFormulaeClasses(0)._2(0) should equal(10)
    module.underlyingFormulaeClasses(0)._2(1) should equal(0.5)
  }

  "The ProcFSSimpleCpuModule object" should "build correctly the companion class" in {
    val module = ProcFSCpuSimpleModule()

    module.underlyingSensorsClasses.size should equal(1)
    module.underlyingSensorsClasses(0)._1 should equal(classOf[CpuSensor])
    module.underlyingSensorsClasses(0)._2.size should equal(1)
    module.underlyingSensorsClasses(0)._2(0).getClass should equal(classOf[LinuxHelper])
    module.underlyingSensorsClasses(0)._2(0).asInstanceOf[LinuxHelper].frequenciesPath should equal("p1/%?core")
    module.underlyingSensorsClasses(0)._2(0).asInstanceOf[LinuxHelper].taskPath should equal("p2/%?pid")
    module.underlyingSensorsClasses(0)._2(0).asInstanceOf[LinuxHelper].globalStatPath should equal("p3")
    module.underlyingSensorsClasses(0)._2(0).asInstanceOf[LinuxHelper].processStatPath should equal("p4/%?pid")
    module.underlyingSensorsClasses(0)._2(0).asInstanceOf[LinuxHelper].timeInStatePath should equal("p5")
    module.underlyingSensorsClasses(0)._2(0).asInstanceOf[LinuxHelper].topology should equal(Map(0 -> Set(0, 4), 1 -> Set(1, 5), 2 -> Set(2, 6), 3 -> Set(3, 7)))

    module.underlyingFormulaeClasses.size should equal(1)
    module.underlyingFormulaeClasses(0)._1 should equal(classOf[CpuFormula])
    module.underlyingFormulaeClasses(0)._2.size should equal(2)
    module.underlyingFormulaeClasses(0)._2(0) should equal(120)
    module.underlyingFormulaeClasses(0)._2(1) should equal(0.80)
  }

  "The SigarCpuSimpleModule object" should "build correctly the companion class" in {
    val module = SigarCpuSimpleModule()

    module.underlyingSensorsClasses.size should equal(1)
    module.underlyingSensorsClasses(0)._1 should equal(classOf[CpuSensor])
    module.underlyingSensorsClasses(0)._2.size should equal(1)
    module.underlyingSensorsClasses(0)._2(0).getClass should equal(classOf[SigarHelper])
    module.underlyingSensorsClasses(0)._2(0).asInstanceOf[SigarHelper].libNativePath should equal("p2")

    module.underlyingFormulaeClasses.size should equal(1)
    module.underlyingFormulaeClasses(0)._1 should equal(classOf[CpuFormula])
    module.underlyingFormulaeClasses(0)._2.size should equal(2)
    module.underlyingFormulaeClasses(0)._2(0) should equal(120)
    module.underlyingFormulaeClasses(0)._2(1) should equal(0.80)
  }
}
