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
package org.powerapi.module.disk.simple

import akka.util.Timeout
import org.powerapi.UnitTest
import org.powerapi.core.{Disk, OSHelper}
import org.scalamock.scalatest.MockFactory

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class DiskSimpleModulesSuite extends UnitTest with MockFactory {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.shutdown()
  }

  "The DiskSimpleModule class" should "create the underlying classes (sensor/formula)" in {
    val osHelper = mock[OSHelper]

    val module = new DiskSimpleModule(osHelper, Seq(Disk("sda", 8, 0)), 1.second, Map("sda" -> Map(
      "read" -> Seq(PieceWiseFunction(Condition("<=", 1e06), Seq(2, 1e-8)), PieceWiseFunction(Condition(">", 1e06), Seq(3, 1e-7))),
      "write" -> Seq(PieceWiseFunction(Condition("<=", 1e9), Seq(5, 4e-8)), PieceWiseFunction(Condition(">", 1e09), Seq(6, 1e-6)))
    )))

    module.sensor.get._1 should equal(classOf[DiskSimpleSensor])
    module.sensor.get._2.size should equal(2)
    module.sensor.get._2(0) should equal(osHelper)
    module.sensor.get._2(1).asInstanceOf[Seq[Disk]] should contain theSameElementsAs Seq(Disk("sda", 8, 0))

    module.formula.get._1 should equal(classOf[DiskSimpleFormula])
    module.formula.get._2.size should equal(2)
    module.formula.get._2(0).asInstanceOf[FiniteDuration] should equal(1.second)
    module.formula.get._2(1).asInstanceOf[Map[String, Map[String, Seq[Double]]]] should contain theSameElementsAs Map("sda" -> Map(
      "read" -> Seq(PieceWiseFunction(Condition("<=", 1e06), Seq(2, 1e-8)), PieceWiseFunction(Condition(">", 1e06), Seq(3, 1e-7))),
      "write" -> Seq(PieceWiseFunction(Condition("<=", 1e9), Seq(5, 4e-8)), PieceWiseFunction(Condition(">", 1e09), Seq(6, 1e-6)))
    ))
  }
}
