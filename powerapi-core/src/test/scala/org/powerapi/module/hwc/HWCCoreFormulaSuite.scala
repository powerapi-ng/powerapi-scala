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
package org.powerapi.module.hwc

import java.security.MessageDigest
import java.util.UUID

import akka.actor.Props
import akka.pattern.gracefulStop
import akka.testkit.{EventFilter, TestActorRef}
import akka.util.Timeout
import com.twitter.util.{Await, Duration, JavaTimer}
import com.twitter.zk.ZkClient
import org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE
import org.powerapi.UnitTest
import org.powerapi.core.power._
import org.powerapi.core.target.{All, Target, intToProcess}
import org.powerapi.core.{MessageBus, Tick}
import org.powerapi.module.FormulaChannel.{startFormula, stopFormula}
import org.powerapi.module.Formulas
import org.powerapi.module.PowerChannel.{RawPowerReport, subscribeRawPowerReport}
import org.powerapi.module.hwc.PowerModelJsonProtocol._
import org.powerapi.module.hwc.HWCChannel.{HWC, publishHWCReport}
import org.scalamock.scalatest.MockFactory
import spray.json._

import scala.concurrent.duration.DurationInt
import scala.collection.JavaConverters._

class HWCCoreFormulaSuite extends UnitTest with MockFactory {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.terminate()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  /*trait Formulae {
    var formulae = Map[Double, List[Double]]()
    formulae += 12d -> List(85.7545270697, 1.10006565433e-08, -2.0341944068e-18)
    formulae += 13d -> List(87.0324917754, 9.03486530986e-09, -1.31575869787e-18)
    formulae += 14d -> List(86.3094440375, 1.04895773556e-08, -1.61982669617e-18)
    formulae += 15d -> List(88.2194900717, 8.71468661777e-09, -1.12354133527e-18)
    formulae += 16d -> List(85.8010062547, 1.05239105674e-08, -1.34813984791e-18)
    formulae += 17d -> List(85.5127064474, 1.05732955159e-08, -1.28040830962e-18)
    formulae += 18d -> List(85.5593567382, 1.07921513277e-08, -1.22419197787e-18)
    formulae += 19d -> List(87.2004521609, 9.99728883739e-09, -9.9514346029e-19)
    formulae += 20d -> List(87.7358230435, 1.00553994023e-08, -1.00002335486e-18)
    formulae += 21d -> List(94.4635683042, 4.83140424765e-09, 4.25218895447e-20)
    formulae += 22d -> List(104.356371072, 3.75414807806e-09, 6.73289818651e-20)
  }*/

  "A HWCCoreFormula" should "process a SensorReport and then publish a RawPowerReport" in new Bus {
    val muid = UUID.randomUUID()
    val target: Target = All
    var formulae = Seq(PowerModel("S0", Seq(100, 1, 2)), PowerModel("S1", Seq(200, 10, 11)))
    var formulaS0 = formulae.find(_.socket == "S0").get.coefficients
    var formulaS1 = formulae.find(_.socket == "S1").get.coefficients

    implicit val timer = new JavaTimer(true)

    val likwidHelper = mock[LikwidHelper]
    likwidHelper.getCpuInfo _ expects() returning CpuInfo(0, 0, 0, 0, 0, "Intel(R) Xeon(R) CPU E5-2630 v3 @ 2.40GHz", "", "", "", 0, 0, 0, 0, 0, 0, 0)
    likwidHelper.getAffinityDomains _ expects() returning AffinityDomains(0, 0, 0, 0, 0, 0, Seq(AffinityDomain("S0", 2, Seq(0, 16, 2, 18)), AffinityDomain("S1", 2, Seq(1, 17, 3, 19))))

    var version = 0

    val zk = ZkClient("localhost:2181", Duration.fromSeconds(10)).withAcl(OPEN_ACL_UNSAFE.asScala)
    val zNode = Await.result(zk("/e5-2630").create(), Duration.fromSeconds(10))
    Await.result(zNode.setData(formulae.toJson.toString.getBytes, version), Duration.fromSeconds(10))
    version += 1

    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }

    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 1000
    }

    val formulas = TestActorRef(Props(classOf[Formulas], eventBus), "formulas")
    EventFilter.info(occurrences = 1, start = s"formula is started, class: ${classOf[HWCCoreFormula].getName}").intercept({
      startFormula(muid, target, classOf[HWCCoreFormula], Seq(eventBus, muid, target, likwidHelper, zk))(eventBus)
    })
    subscribeRawPowerReport(muid)(eventBus)(testActor)

//    val values = Seq[HWC](
//      HWC(HWThread(0, 0, 0, 0, 0), "CPU_CLK_UNHALTED_CORE:FIXC1", 650000000d),
//      HWC(HWThread(1, 0, 0, 1, 1), "CPU_CLK_UNHALTED_CORE:FIXC1", 651000000d),
//      HWC(HWThread(0, 0, 0, 0, 0), "CPU_CLK_UNHALTED_REF:FIXC2", 34475589d),
//      HWC(HWThread(1, 0, 0, 1, 1), "CPU_CLK_UNHALTED_REF:FIXC2", 34075589d),
//      HWC(HWThread(2, 1, 0, 2, 2), "CPU_CLK_UNHALTED_CORE:FIXC1", 0d),
//      HWC(HWThread(3, 1, 0, 3, 3), "CPU_CLK_UNHALTED_CORE:FIXC1", 0d),
//      HWC(HWThread(2, 1, 0, 2, 2), "CPU_CLK_UNHALTED_REF:FIXC2", 0d),
//      HWC(HWThread(3, 1, 0, 3, 3), "CPU_CLK_UNHALTED_REF:FIXC2", 0d)
//    )

    /*var values = Seq[HWC](
      HWC(HWThread(0, 0, 0, 0, 0), "CPU_CLK_UNHALTED_CORE:FIXC1", 1E9),
      HWC(HWThread(1, 0, 0, 1, 1), "CPU_CLK_UNHALTED_CORE:FIXC1", 1E9),
      HWC(HWThread(0, 0, 0, 0, 0), "CPU_CLK_UNHALTED_REF:FIXC2", 34475589d),
      HWC(HWThread(1, 0, 0, 1, 1), "CPU_CLK_UNHALTED_REF:FIXC2", 34075589d),
      HWC(HWThread(2, 1, 0, 2, 2), "CPU_CLK_UNHALTED_CORE:FIXC1", 0d),
      HWC(HWThread(3, 1, 0, 3, 3), "CPU_CLK_UNHALTED_CORE:FIXC1", 0d),
      HWC(HWThread(2, 1, 0, 2, 2), "CPU_CLK_UNHALTED_REF:FIXC2", 0d),
      HWC(HWThread(3, 1, 0, 3, 3), "CPU_CLK_UNHALTED_REF:FIXC2", 0d)
    )*/

    var values = Seq[HWC](
      HWC(HWThread(1, 2, 1, 1, 1), "CPU_CLK_UNHALTED_CORE:FIXC1", 3e09),
      HWC(HWThread(1, 2, 1, 1, 1), "CPU_CLK_UNHALTED_REF:FIXC1", -1),
      HWC(HWThread(17, 2, 1, 17, 17), "CPU_CLK_UNHALTED_CORE:FIXC1", 3e09),
      HWC(HWThread(17, 2, 1, 17, 17), "CPU_CLK_UNHALTED_REF:FIXC1", -1),
      HWC(HWThread(3, 3, 1, 3, 3), "CPU_CLK_UNHALTED_CORE:FIXC1", 4e09),
      HWC(HWThread(3, 3, 1, 3, 3), "CPU_CLK_UNHALTED_REF:FIXC1", -1),
      HWC(HWThread(19, 3, 1, 19, 19), "CPU_CLK_UNHALTED_CORE:FIXC1", 4e09),
      HWC(HWThread(19, 3, 1, 19, 19), "CPU_CLK_UNHALTED_REF:FIXC1", -1),
      HWC(HWThread(0, 0, 0, 0, 0), "CPU_CLK_UNHALTED_CORE:FIXC1", 1e09),
      HWC(HWThread(0, 0, 0, 0, 0), "CPU_CLK_UNHALTED_REF:FIXC1", -1),
      HWC(HWThread(16, 0, 0, 16, 16), "CPU_CLK_UNHALTED_CORE:FIXC1", 1e09),
      HWC(HWThread(16, 0, 0, 16, 16), "CPU_CLK_UNHALTED_REF:FIXC1", -1),
      HWC(HWThread(2, 1, 0, 2, 2), "CPU_CLK_UNHALTED_CORE:FIXC1", 2e09),
      HWC(HWThread(2, 1, 0, 2, 2), "CPU_CLK_UNHALTED_REF:FIXC1", -1),
      HWC(HWThread(18, 1, 0, 18, 18), "CPU_CLK_UNHALTED_CORE:FIXC1", 2e09),
      HWC(HWThread(18, 1, 0, 18, 18), "CPU_CLK_UNHALTED_REF:FIXC1", -1)
    )

    val expectedPower1 = 2 * formulaS0(1) + 4 * formulaS0(2) + 6 * formulaS1(1) + 8 * formulaS1(2)

    publishHWCReport(muid, target, values, tick1)(eventBus)
    var rawPowerReport = expectMsgClass(classOf[RawPowerReport])
    rawPowerReport.muid should equal(muid)
    rawPowerReport.target should equal(target)
    rawPowerReport.power should equal(expectedPower1.W)
    rawPowerReport.device should equal("cpu")
    rawPowerReport.tick.asInstanceOf[ZKTick] should equal(ZKTick(tick1.topic, tick1.timestamp, 100 + 200, MessageDigest.getInstance("MD5").digest(formulae.toJson.toString.getBytes).map(0xFF & _).map { "%02x".format(_) }.mkString))

    formulae = Seq(PowerModel("S0", Seq(400, 10, 20)), PowerModel("S1", Seq(500, 30, 40)))
    formulaS0 = formulae.find(_.socket == "S0").get.coefficients
    formulaS1 = formulae.find(_.socket == "S1").get.coefficients
    Await.result(zNode.setData(formulae.toJson.toString.getBytes, version), Duration.fromSeconds(10))
    version += 1
    values = Seq[HWC](
      HWC(HWThread(1, 2, 1, 1, 1), "CPU_CLK_UNHALTED_CORE:FIXC1", 1e09),
      HWC(HWThread(1, 2, 1, 1, 1), "CPU_CLK_UNHALTED_REF:FIXC1", -1),
      HWC(HWThread(17, 2, 1, 17, 17), "CPU_CLK_UNHALTED_CORE:FIXC1", 1e09),
      HWC(HWThread(17, 2, 1, 17, 17), "CPU_CLK_UNHALTED_REF:FIXC1", -1),
      HWC(HWThread(3, 3, 1, 3, 3), "CPU_CLK_UNHALTED_CORE:FIXC1", 2e09),
      HWC(HWThread(3, 3, 1, 3, 3), "CPU_CLK_UNHALTED_REF:FIXC1", -1),
      HWC(HWThread(19, 3, 1, 19, 19), "CPU_CLK_UNHALTED_CORE:FIXC1", 2e09),
      HWC(HWThread(19, 3, 1, 19, 19), "CPU_CLK_UNHALTED_REF:FIXC1", -1),
      HWC(HWThread(0, 0, 0, 0, 0), "CPU_CLK_UNHALTED_CORE:FIXC1", 3e09),
      HWC(HWThread(0, 0, 0, 0, 0), "CPU_CLK_UNHALTED_REF:FIXC1", -1),
      HWC(HWThread(16, 0, 0, 16, 16), "CPU_CLK_UNHALTED_CORE:FIXC1", 3e09),
      HWC(HWThread(16, 0, 0, 16, 16), "CPU_CLK_UNHALTED_REF:FIXC1", -1),
      HWC(HWThread(2, 1, 0, 2, 2), "CPU_CLK_UNHALTED_CORE:FIXC1", 4e09),
      HWC(HWThread(2, 1, 0, 2, 2), "CPU_CLK_UNHALTED_REF:FIXC1", -1),
      HWC(HWThread(18, 1, 0, 18, 18), "CPU_CLK_UNHALTED_CORE:FIXC1", 4e09),
      HWC(HWThread(18, 1, 0, 18, 18), "CPU_CLK_UNHALTED_REF:FIXC1", -1)
    )
    val expectedPower2 = 6 * formulaS0(1) + 8 * formulaS0(2) + 2 * formulaS1(1) + 4 * formulaS1(2)

    Thread.sleep(5.seconds.toMillis)

    publishHWCReport(muid, target, values, tick2)(eventBus)
    rawPowerReport = expectMsgClass(classOf[RawPowerReport])
    rawPowerReport.muid should equal(muid)
    rawPowerReport.target should equal(target)
    rawPowerReport.power should equal(expectedPower2.W)
    rawPowerReport.device should equal("cpu")
    rawPowerReport.tick.asInstanceOf[ZKTick] should equal(ZKTick(tick2.topic, tick2.timestamp, 400 + 500, MessageDigest.getInstance("MD5").digest(formulae.toJson.toString.getBytes).map(0xFF & _).map { "%02x".format(_) }.mkString))

    EventFilter.info(occurrences = 1, start = s"formula is stopped, class: ${classOf[HWCCoreFormula].getName}").intercept({
      stopFormula(muid)(eventBus)
    })

    publishHWCReport(muid, target, values, tick2)(eventBus)
    expectNoMsg()

    Await.result(zk("/e5-2630").delete(version), Duration.fromSeconds(10))

    scala.concurrent.Await.result(gracefulStop(formulas, timeout.duration), timeout.duration)
  }
}
