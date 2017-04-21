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

import akka.util.Timeout
import com.twitter.zk.ZkClient
import org.powerapi.UnitTest
import org.powerapi.core.OSHelper
import org.scalamock.scalatest.MockFactory

import scala.concurrent.duration.DurationInt

class HWCCoreModulesSuite extends UnitTest with MockFactory {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.terminate()
  }

  "The HWCCoreModule class" should "create the underlying classes (sensor/formula)" in {
    val osHelper = mock[OSHelper]
    val likwidHelper = mock[LikwidHelper]
    val cHelper = mock[CHelper]
    val zkClient = mock[ZkClient]

    val module = new HWCCoreModule(osHelper, likwidHelper, cHelper, Seq("e1"), zkClient)

    module.sensor.get._1 should equal(classOf[HWCCoreSensor])
    module.sensor.get._2.size should equal(4)
    module.sensor.get._2(0) should equal(osHelper)
    module.sensor.get._2(1) should equal(likwidHelper)
    module.sensor.get._2(2) should equal(cHelper)
    module.sensor.get._2(3) should equal(Seq("e1"))

    module.formula.get._1 should equal(classOf[HWCCoreFormula])
    module.formula.get._2.size should equal(2)
    module.formula.get._2(0) should equal(likwidHelper)
    module.formula.get._2(1) should equal(zkClient)
  }

  "The HWCCoreModule object" should "build correctly the companion class" in {
    val osHelper = mock[OSHelper]
    val likwidHelper = mock[LikwidHelper]
    val cHelper = mock[CHelper]
    val zkClient = mock[ZkClient]

    val module1 = HWCCoreModule(osHelper = osHelper, likwidHelper = likwidHelper, cHelper = cHelper, zkClient = zkClient)
    val module2 = HWCCoreModule(Some("hwc"), osHelper = osHelper, likwidHelper = likwidHelper, cHelper = cHelper, zkClient)

    module1.sensor.get._1 should equal(classOf[HWCCoreSensor])
    module1.sensor.get._2.size should equal(4)
    module1.sensor.get._2(0) should equal(osHelper)
    module1.sensor.get._2(1) should equal(likwidHelper)
    module1.sensor.get._2(2) should equal(cHelper)
    module1.sensor.get._2(3) should equal(Seq("CPU_CLK_UNHALTED_CORE:FIXC1", "CPU_CLK_UNHALTED_REF:FIXC2"))

    module1.formula.get._1 should equal(classOf[HWCCoreFormula])
    module1.formula.get._2.size should equal(2)
    module1.formula.get._2(0) should equal(likwidHelper)
    module1.formula.get._2(1) should equal(zkClient)

    module2.sensor.get._1 should equal(classOf[HWCCoreSensor])
    module1.sensor.get._2.size should equal(4)
    module1.sensor.get._2(0) should equal(osHelper)
    module1.sensor.get._2(1) should equal(likwidHelper)
    module1.sensor.get._2(2) should equal(cHelper)
    module2.sensor.get._2(3) should equal(Seq("event"))


    module2.formula.get._1 should equal(classOf[HWCCoreFormula])
    module2.formula.get._2.size should equal(2)
    module2.formula.get._2(0) should equal(likwidHelper)
    module2.formula.get._2(1) should equal(zkClient)
  }

  "The HWCCoreSensorModule object" should "build correctly the companion class" in {
    val osHelper = mock[OSHelper]
    val likwidHelper = mock[LikwidHelper]
    val cHelper = mock[CHelper]

    val module1 = HWCCoreSensorModule(osHelper = osHelper, likwidHelper = likwidHelper, cHelper = cHelper)
    val module2 = HWCCoreSensorModule(Some("hwc"), osHelper = osHelper, likwidHelper = likwidHelper, cHelper = cHelper)

    module1.sensor.get._1 should equal(classOf[HWCCoreSensor])
    module1.sensor.get._2.size should equal(4)
    module1.sensor.get._2(0) should equal(osHelper)
    module1.sensor.get._2(1) should equal(likwidHelper)
    module1.sensor.get._2(2) should equal(cHelper)
    module1.sensor.get._2(3) should equal(Seq("CPU_CLK_UNHALTED_CORE:FIXC1", "CPU_CLK_UNHALTED_REF:FIXC2"))

    module1.formula should equal(None)

    module2.sensor.get._1 should equal(classOf[HWCCoreSensor])
    module1.sensor.get._2.size should equal(4)
    module1.sensor.get._2(0) should equal(osHelper)
    module1.sensor.get._2(1) should equal(likwidHelper)
    module1.sensor.get._2(2) should equal(cHelper)
    module2.sensor.get._2(3) should equal(Seq("event"))

    module2.formula should equal(None)
  }
}
