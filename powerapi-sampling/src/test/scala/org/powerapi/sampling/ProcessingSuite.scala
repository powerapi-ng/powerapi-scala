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
package org.powerapi.sampling

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import org.powerapi.UnitTest
import org.saddle.io.{CsvFile, CsvParams, CsvParser}
import scala.concurrent.duration.DurationInt
import scalax.file.Path

class ProcessingSuite(system: ActorSystem) extends UnitTest(system) {

  def this() = this(ActorSystem("ProcessingSuite"))

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val basepath = getClass.getResource("/").getPath

  "The Processing step" should "process the sample files and create the files that will be used during the regression" in {
    val samplingConfiguration = new SamplingConfiguration {
      override lazy val baseOutput = "output-"
      override lazy val separator = "="
    }

    new Processing(s"${basepath}samples", "/tmp/processing", samplingConfiguration).run()

    val expectedPaths = Path("/") / (s"${basepath}processing", '/') * "*.csv"
    val prDataPaths = Path("/") / ("/tmp/processing", '/') * "*.csv"

    prDataPaths.size should equal(expectedPaths.size)

    for(path <- expectedPaths) {
      val expectedCSV = CsvFile(path.path)
      val prDataCSV = CsvFile(s"/tmp/processing/${path.name}")
      val expectedMat = CsvParser.parse(List(1,2), CsvParams(skipLines = 1))(expectedCSV).mapValues(CsvParser.parseDouble).toMat
      val prDataMat = CsvParser.parse(List(1,2), CsvParams(skipLines = 1))(prDataCSV).mapValues(CsvParser.parseDouble).toMat

      for(index <- 0 until expectedMat.numCols) {
        prDataMat.col(index).contents should contain theSameElementsAs expectedMat.col(index).contents
      }
    }
  }
}
