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
package org.powerapi.sampling

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import org.joda.time.format.PeriodFormatterBuilder
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

  trait Formulae {
    var formulae = List[String]()
    formulae :+= "powerapi.libpfm.formulae.cycles = ["
    formulae :+= "  { coefficient = 12.0, formula = [92.20886561572331,2.285714027168686E-8,-1.416580072868971E-17] }"
    formulae :+= "  { coefficient = 13.0, formula = [92.48173723023501,2.1188435019184853E-8,-1.1164115851610073E-17] }"
    formulae :+= "  { coefficient = 14.0, formula = [91.18277678547861,2.795388215113586E-8,-1.5345230874242293E-17] }"
    formulae :+= "  { coefficient = 15.0, formula = [91.58387287172661,2.9734425570507765E-8,-1.7544248591494286E-17] }"
    formulae :+= "  { coefficient = 16.0, formula = [92.02244294383439,2.7807408431676527E-8,-1.2746904725498715E-17] }"
    formulae :+= "  { coefficient = 17.0, formula = [91.3500222816532,3.0366622403587484E-8,-1.3854655417383513E-17] }"
    formulae :+= "  { coefficient = 18.0, formula = [91.33481852488529,3.286018826888694E-8,-1.409692587879552E-17] }"
    formulae :+= "  { coefficient = 19.0, formula = [91.24010397916015,3.5201585320905026E-8,-1.4560221154346024E-17] }"
    formulae :+= "  { coefficient = 20.0, formula = [91.62432342022942,3.810191894497629E-8,-1.4857999181001822E-17] }"
    formulae :+= "  { coefficient = 21.0, formula = [92.03780565000716,5.119368902831622E-8,-2.166176426151429E-17] }"
    formulae :+= "  { coefficient = 22.0, formula = [90.26543958762136,5.472361694273432E-8,-2.17898798570039E-17] }"
    formulae :+= "]"
  }

  "The Processing step" should "process the sample files and compute the cpu formulae" in new Formulae {
    val configuration = new SamplingConfiguration {
      override lazy val processingDir = "/tmp/processing"
      override lazy val computingDir = "/tmp/formulae"
      override lazy val unhaltedCycles = "CPU_CLK_UNHALTED:THREAD_P"
      override lazy val refCycles = "CPU_CLK_UNHALTED:REF_P"
      override lazy val baseFrequency = 0.133
      override lazy val maxFrequency = 2.66
      override lazy val steps = List(100, 25)
      override lazy val topology = Map(0 -> Set(0, 4), 1 -> Set(1, 5), 2 -> Set(2, 6), 3 -> Set(3, 7))
    }

    new Processing(s"${basepath}samples", configuration, new PolynomialRegression).run()

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

    (Path("/") / ("/tmp/formulae/libpfm-formula.conf", '/')).lines().toList should contain theSameElementsAs formulae
  }
}
