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
package org.powerapi.reporter

import java.io.File
import java.util.UUID

import scala.concurrent.duration.DurationInt

import akka.util.Timeout

import org.powerapi.UnitTest
import org.powerapi.core.power._
import org.powerapi.core.target.{Application, Process, Target}

class FileDisplaySuite extends UnitTest {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.shutdown()
  }

  "A FileDisplay" should "display an AggPowerReport message in a File" in {
    val muid = UUID.randomUUID()
    val timestamp = System.currentTimeMillis()
    val targets = Set[Target](Application("firefox"), Process(1), Process(2))
    val devices = Set[String]("cpu", "gpu", "ssd")
    val power = 10.W
    val file = new File("output-file.dat")
    file.delete()

    val out = new FileDisplay("output-file.dat")
    out.display(muid, timestamp, targets, devices, 10.W)
    out.output.lines().toSeq should contain theSameElementsAs Seq(
      s"muid=$muid;timestamp=$timestamp;targets=${targets.mkString(",")};devices=${devices.mkString(",")};power=${power.toMilliWatts} mW"
    )

    file.delete()
  }
}

