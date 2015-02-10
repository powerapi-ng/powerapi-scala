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

object PolynomialRegression {
  import org.apache.logging.log4j.LogManager

  private val log = LogManager.getLogger

  def apply(prDataDir: String, formulaeDir: String, degree: Int): Unit = {
    import org.ejml.data.DenseMatrix64F
    import org.ejml.ops.CommonOps
    import org.saddle.io.{CsvParams, CsvParser, CsvFile}
    import org.saddle.{Mat, Vec}
    import scalax.file.Path

    var coefficients = Map[Double, Array[Double]]()

    for(path <- Path("/") / (prDataDir, '/') * "*.csv") {
      val coefficient = path.name.replace(".csv", "")
      val data = CsvParser.parse(List(1,2), CsvParams(skipLines = 1))(CsvFile(path.path)).mapValues(CsvParser.parseDouble).toMat
      val unhaltedCycles = data.col(0)
      val powers = data.col(1)

      /**
       * Compute the xi^j sum (1 to degree + degree) and create the line with the maximum number of values.
       * It allows to not compute the same xi^j many times in the matrix construction.
       */
      val line = unhaltedCycles.length.toDouble +: (for(j <- 1 to degree * 2) yield unhaltedCycles.map(xi => math.pow(xi, j)).sum)

      /**
       * Take the right values into line by playing with the intervals.
       */
      val a = (for(j <- 0 to degree) yield line.slice(j, degree + j + 1)).flatten.toArray
      val A = new DenseMatrix64F(degree + 1, a.size / (degree + 1), true, a: _*)
      CommonOps.invert(A)
      val invertedA = Mat(A.getNumRows, A.getNumCols, A.getData)

      val b = (powers.sum +: (for(j <- 1 to degree) yield (unhaltedCycles.map(xi => math.pow(xi, j)) * powers).sum)).toArray
      val B = Vec(b)

      val results = (invertedA dot B).toVec
      coefficients += coefficient.toDouble -> results.toSeq.toArray

      /**
       * Error computations for logging
       *
       * @see http://www.stat.purdue.edu/~xuanyaoh/stat350/xyApr6Lec26.pdf
       */
      lazy val estimatedPowers = for(xi <- unhaltedCycles) yield results.at(0).toDouble + results.at(1).toDouble * xi + results.at(2).toDouble * xi * xi
      lazy val sst: Double = ((powers - powers.mean) ** 2).sum
      lazy val sse: Double = ((powers - estimatedPowers) ** 2).sum
      lazy val rsquared: Double = 1 - (sse / sst)
      lazy val mse: Double = sse / unhaltedCycles.length
      lazy val se: Double = math.sqrt(mse)

      log.debug(s"coefficient: $coefficient; r^2: $rsquared; mean squared error: $mse; standard deviation: $se")
    }

    if((Path("/") / (prDataDir, '/')).exists) {
      (Path("/") / (formulaeDir, '/')).deleteRecursively(force = true)
      (Path("/") / (formulaeDir, '/')).createDirectory(failIfExists = false)
      var lines = List[String]("powerapi.libpfm.formulae.cycles = [")

      for(freqCoeff <- coefficients.keys.toList.sorted) {
        lines :+= s"  { coefficient = $freqCoeff, formula = [${coefficients(freqCoeff).mkString(",")}] }"
      }

      lines :+= "]"

      (Path("/") / (formulaeDir, '/') / ("libpfm-formula.conf", '/')).writeStrings(lines, "\n")
    }
  }
}
