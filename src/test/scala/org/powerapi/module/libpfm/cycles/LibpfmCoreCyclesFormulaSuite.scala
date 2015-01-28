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
package org.powerapi.module.libpfm.cycles

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.powerapi.UnitTest

class LibpfmCoreCyclesFormulaSuite(system: ActorSystem) extends UnitTest(system) {
  import org.powerapi.core.MessageBus

  def this() = this(ActorSystem("LibpfmCoreCyclesFormulaSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  trait Formulae {
    var formulae = Map[Double, Array[Double]]()
    formulae += 12d -> Array(85.7545270697, 1.10006565433e-08, -2.0341944068e-18)
    formulae += 13d -> Array(87.0324917754, 9.03486530986e-09, -1.31575869787e-18)
    formulae += 14d -> Array(86.3094440375, 1.04895773556e-08, -1.61982669617e-18)
    formulae += 15d -> Array(88.2194900717, 8.71468661777e-09, -1.12354133527e-18)
    formulae += 16d -> Array(85.8010062547, 1.05239105674e-08, -1.34813984791e-18)
    formulae += 17d -> Array(85.5127064474, 1.05732955159e-08, -1.28040830962e-18)
    formulae += 18d -> Array(85.5593567382, 1.07921513277e-08, -1.22419197787e-18)
    formulae += 19d -> Array(87.2004521609, 9.99728883739e-09, -9.9514346029e-19)
    formulae += 20d -> Array(87.7358230435, 1.00553994023e-08, -1.00002335486e-18)
    formulae += 21d -> Array(94.4635683042, 4.83140424765e-09, 4.25218895447e-20)
    formulae += 22d -> Array(104.356371072, 3.75414807806e-09, 6.73289818651e-20)
  }

  "A LibpfmCoreCycles" should "compute the power when it receives a specific report (cycles/ref-cycles)" in new Bus with Formulae {
    import akka.actor.Props
    import akka.testkit.TestActorRef
    import java.util.UUID
    import org.powerapi.core.ClockChannel.ClockTick
    import org.powerapi.core.power._
    import org.powerapi.core.target.{intToProcess, Process}
    import org.powerapi.module.libpfm.PerformanceCounterChannel.{publishPCReport, PCWrapper}
    import org.powerapi.module.PowerChannel.{PowerReport, subscribePowerReport}
    import scala.concurrent.duration.DurationDouble
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.Future

    val actor = TestActorRef(Props(classOf[LibpfmCoreCyclesFormula], eventBus), "libpfm-cycles-formula1")(system)
    val muid = UUID.randomUUID()
    val tick = ClockTick("clock", 1.seconds)
    subscribePowerReport(muid)(eventBus)(testActor)

    var wrappers = List[PCWrapper]()
    wrappers :+= PCWrapper(0, "CPU_CLK_UNHALTED:THREAD_P", List(Future[Long] {2606040442l}, Future[Long] {2606040442l}))
    wrappers :+= PCWrapper(1, "CPU_CLK_UNHALTED:THREAD_P", List(Future[Long] {0l}, Future[Long] {0l}))
    wrappers :+= PCWrapper(2, "CPU_CLK_UNHALTED:THREAD_P", List(Future[Long] {2606040442l}, Future[Long] {0l}))
    wrappers :+= PCWrapper(3, "CPU_CLK_UNHALTED:THREAD_P", List(Future[Long] {0l}, Future[Long] {0l}))
    wrappers :+= PCWrapper(0, "CPU_CLK_UNHALTED:REF_P", List(Future[Long] {130307030l}, Future[Long] {130307030l}))
    wrappers :+= PCWrapper(1, "CPU_CLK_UNHALTED:REF_P", List(Future[Long] {0l}, Future[Long] {0l}))
    wrappers :+= PCWrapper(2, "CPU_CLK_UNHALTED:REF_P", List(Future[Long] {130307030l}, Future[Long] {0l}))
    wrappers :+= PCWrapper(3, "CPU_CLK_UNHALTED:REF_P", List(Future[Long] {0l}, Future[Long] {0l}))

    var formula = Array[Double]()
    var power = 0d

    // Core 0
    formula = formulae(math.round((2606040442l + 2606040442l) / (130307030l + 130307030l).toDouble).toDouble)
    power += formula(1) * (2606040442l + 2606040442l) + formula(2) * math.pow(2606040442l + 2606040442l, 2)

    // Core 2
    formula = formulae(math.round(2606040442l / 130307030l.toDouble).toDouble)
    power += formula(1) * 2606040442l + formula(2) * math.pow(2606040442l, 2)

    actor.underlyingActor.asInstanceOf[LibpfmCoreCyclesFormula].formulae.keys should be(formulae.keys)
    for((coefficient, formula) <- formulae) {
      actor.underlyingActor.asInstanceOf[LibpfmCoreCyclesFormula].formulae(coefficient) should be(formula)
    }

    actor.underlyingActor.asInstanceOf[LibpfmCoreCyclesFormula].samplingInterval should equal(1.seconds)
    actor.underlyingActor.asInstanceOf[LibpfmCoreCyclesFormula].cyclesThreadName should equal("CPU_CLK_UNHALTED:THREAD_P")
    actor.underlyingActor.asInstanceOf[LibpfmCoreCyclesFormula].cyclesRefName should equal("CPU_CLK_UNHALTED:REF_P")

    publishPCReport(muid, 1, wrappers, tick)(eventBus)

    val ret = expectMsgClass(classOf[PowerReport])
    ret.muid should equal(muid)
    ret.target should equal(Process(1))
    ret.power should equal(power.W)
    ret.device should equal("cpu")
    ret.tick should equal(tick)
  }
}
