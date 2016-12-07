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
package org.powerapi.sampling.cpu

import java.io.File

import scala.concurrent.duration.DurationInt
import akka.util.Timeout
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.{SuffixFileFilter, TrueFileFilter}
import org.powerapi.UnitTest

import scala.collection.JavaConverters._
import scala.io.Source

class ProcessingSuite extends UnitTest {

  val timeout = Timeout(1.seconds)
  val basepath = getClass.getResource("/").getPath

  override def afterAll() = {
    system.terminate()
  }

  "The Processing step" should "process the sample files and create the files that will be used during the regression" in {
    val samplingConfiguration = new SamplingConfiguration {
      override lazy val baseOutput = "output-"
      override lazy val separator = "="
    }

    new Processing(s"${basepath}samples", "/tmp/processing", samplingConfiguration).run()


    val expectedFiles = FileUtils.listFiles(new File(s"${basepath}processing"), new SuffixFileFilter(".csv"), TrueFileFilter.INSTANCE).asScala.toArray
    val prDataFiles = FileUtils.listFiles(new File("/tmp/processing"), new SuffixFileFilter(".csv"), TrueFileFilter.INSTANCE).asScala.toArray

    expectedFiles.size should not equal(0)
    prDataFiles.size should not equal(0)
    prDataFiles.size should equal(expectedFiles.size)

    for (file <- expectedFiles) {
      val expectedCSV = for (line <- Source.fromFile(s"${basepath}processing/${file.getParentFile.getName}/${file.getName}").getLines().toArray) yield line.split(",")
      val expectedData = scala.collection.mutable.SortedMap[String, scala.collection.mutable.ArrayBuffer[Double]]() ++ expectedCSV(0).map(header => (header, scala.collection.mutable.ArrayBuffer[Double]())).toMap
      for (data <- expectedCSV.tail) {
        for (i <- 0 until data.size) {
          expectedData(expectedData.keys.toArray.apply(i)) += data(i).toDouble
        }
      }

      val prDataCSV = for (line <- Source.fromFile(s"/tmp/processing/${file.getParentFile.getName}/${file.getName}").getLines().toArray) yield line.split(",")
      val prData = scala.collection.mutable.SortedMap[String, scala.collection.mutable.ArrayBuffer[Double]]() ++ prDataCSV(0).map(header => (header, scala.collection.mutable.ArrayBuffer[Double]())).toMap
      for (data <- prDataCSV.tail) {
        for (i <- 0 until data.size) {
          prData(prData.keys.toArray.apply(i)) += data(i).toDouble
        }
      }

      prData should contain theSameElementsAs(expectedData)
    }
  }
}
