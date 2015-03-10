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
package org.powerapi

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import org.powerapi.core.MessageBus
import org.powerapi.module.cpu.dvfs.CpuDvfsModule
import org.powerapi.module.cpu.simple.CpuSimpleModule
import org.powerapi.module.libpfm.{LibpfmCoreSensorModule, LibpfmCoreModule}
import org.powerapi.module.powerspy.PowerSpyModule
import org.powerapi.module.rapl.RAPLModule
import scala.concurrent.duration.DurationInt

class PowerMeterSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("CpuSimpleModuleSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  trait EventBus {
    val eventBus = new MessageBus()
  }

  "The PowerMeter companion object" should "allow to load a PowerModule" in {
    PowerMeter.loadModule(new PowerModule {
      lazy val underlyingSensorsClasses = Seq()
      lazy val underlyingFormulaeClasses = Seq()
    })
  }

  "The PowerMeter configuration" should "be correctly read from a resource file" in {
    val configuration = new PowerMeterConfiguration {}
    configuration.timeout should equal(Timeout(10.seconds))
  }

  "The PowerMeter actor" should "load the CpuSimpleModule" in new EventBus {
    val actor = TestActorRef(Props(classOf[PowerMeterActor], eventBus, Seq(CpuSimpleModule()), Timeout(1.seconds)))(system)
    actor.children.size should equal(4)
  }

  it should "load the CpuDvfsModule" in new EventBus {
    val actor = TestActorRef(Props(classOf[PowerMeterActor], eventBus, Seq(CpuDvfsModule()), Timeout(1.seconds)))(system)
    actor.children.size should equal(4)
  }

  it should "load the LibpfmCoreModule" in new EventBus {
    val actor = TestActorRef(Props(classOf[PowerMeterActor], eventBus, Seq(LibpfmCoreModule()), Timeout(1.seconds)))(system)
    actor.children.size should equal(4)
  }

  it should "load the LibpfmCoreSensorModule" in new EventBus {
    val actor = TestActorRef(Props(classOf[PowerMeterActor], eventBus, Seq(LibpfmCoreSensorModule()), Timeout(1.seconds)))(system)
    actor.children.size should equal(3)
    val actor2 = TestActorRef(Props(classOf[PowerMeterActor], eventBus, Seq(LibpfmCoreSensorModule(Set("cycles", "instructions"))), Timeout(1.seconds)))(system)
    actor2.children.size should equal(3)
  }

  it should "load the PowerSpyModule" ignore new EventBus {
    val actor = TestActorRef(Props(classOf[PowerMeterActor], eventBus, Seq(PowerSpyModule()), Timeout(1.seconds)))(system)
    actor.children.size should equal(4)
  }

  it should "load the RAPLModule" in new EventBus {
    val actor = TestActorRef(Props(classOf[PowerMeterActor], eventBus, Seq(RAPLModule()), Timeout(1.seconds)))(system)
    actor.children.size should equal(4)
  }
}
