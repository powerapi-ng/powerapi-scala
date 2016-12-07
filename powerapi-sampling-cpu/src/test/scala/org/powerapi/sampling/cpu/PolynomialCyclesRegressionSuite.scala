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

import scala.concurrent.duration.DurationInt
import akka.util.Timeout
import org.powerapi.UnitTest

import scala.io.Source

class PolynomialCyclesRegressionSuite extends UnitTest {

  val timeout = Timeout(1.seconds)
  val basepath = getClass.getResource("/").getPath

  override def afterAll() = {
    system.terminate()
  }

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
    formulae :+= "  { coefficient = 22.0, formula = [90.26903461985188,5.472369745790217E-8,-2.1792053409198412E-17] }"
    formulae :+= "]"
  }

  "The PolynomialCyclesRegression" should "process the processing files to compute the formulae with unhalted and ref cycles" in new Formulae {
    val polynomCyclesConfiguration = new PolynomCyclesConfiguration {
      override lazy val steps = List(100, 25)
      override lazy val turbo = true
      override lazy val topology = Map(0 -> Set(0, 4), 1 -> Set(1, 5), 2 -> Set(2, 6), 3 -> Set(3, 7))

      override lazy val unhaltedCycles = "CPU_CLK_UNHALTED:THREAD_P"
      override lazy val refCycles = "CPU_CLK_UNHALTED:REF_P"
      override lazy val powers = "powers"
      override lazy val baseFrequency = 0.133
      override lazy val maxFrequency = 2.66
    }

    new PolynomialCyclesRegression(s"${basepath}processing", "/tmp/formulae", polynomCyclesConfiguration).run()
    Source.fromFile("/tmp/formulae/libpfm-formula.conf").getLines().toList should contain theSameElementsAs formulae
  }
}