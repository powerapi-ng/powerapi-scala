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

import scalax.file.Path
import scalax.file.PathMatcher.IsDirectory

import org.apache.logging.log4j.LogManager
import org.ejml.data.DenseMatrix64F
import org.ejml.ops.CommonOps
import org.joda.time.Period
import org.saddle.io.{CsvFile, CsvParser}
import org.saddle.{Mat, Vec}

/**
  * Compute the CPU formulae by using the unhalted and the reference cycles.
  * The resulting formula is a formula of degree 2.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class PolynomialCyclesRegression(processingPath: String, computingPath: String, configuration: PolynomCyclesConfiguration) extends Regression {
  private val log = LogManager.getLogger
  private val degree = 2

  def run(): Unit = {
    val begin = System.currentTimeMillis()

    val maxCoefficient = (configuration.maxFrequency.toDouble * 1e6) / (configuration.baseFrequency * 1e6)
    val frequencies = for (path <- (Path(processingPath, '/') ** IsDirectory).toList) yield path.name.toLong
    val frequenciesNoTB = frequencies.filter { frequency => frequency.toDouble / (configuration.baseFrequency * 1e6) <= maxCoefficient }
    val nbSteps = configuration.steps.size * configuration.topology.head._2.size
    val formulae = scala.collection.mutable.Map[Double, Array[Double]]()

    for (frequency <- frequenciesNoTB) {
      val coefficient = frequency.toDouble / (configuration.baseFrequency * 1e6)
      val parts = (for (path <- (Path(s"$processingPath/$frequency", '/') ** "*.csv").toList) yield path.name.replace(".csv", "").toLong).sorted
      var x = Vec[Double]()
      var y = Vec[Double]()

      for (part <- parts) {
        val frame = CsvParser.parse(CsvFile(s"$processingPath/$frequency/$part.csv")).withRowIndex(0).withColIndex(0).mapValues(CsvParser.parseDouble)
        x = x.concat(Vec(frame.col(s"${configuration.unhaltedCycles.toLowerCase().replace('_', '-').replace(':', '-')}").toMat.col(0).median))
        y = y.concat(Vec(frame.col(s"${configuration.powers.toLowerCase().replace('_', '-').replace(':', '-')}").toMat.col(0).median))
      }

      formulae += coefficient -> compute(Mat(x, y))
    }

    /**
      * We apply a specific algorithm for the turbo frequencies.
      * We try to infer the different coefficients by stressing the processor core by core to activate or not the heuristics.
      * Given the fact we cannot get the "true" power consumption for a core, we subtract the estimated one when it's necessary.
      */
    if (configuration.turbo) {
      val frequency = (configuration.maxFrequency * 1E6).toLong + 1000
      val parts = (for (path <- (Path(s"$processingPath/$frequency", '/') ** "*.csv").toList) yield path.name.replace(".csv", "").toLong).sorted
      val coefficients = for (part <- 1 until(parts.size, nbSteps)) yield {
        val frame = CsvParser.parse(CsvFile(s"$processingPath/$frequency/$part.csv")).withRowIndex(0).withColIndex(0).mapValues(CsvParser.parseDouble)
        var unhaltedCycles = frame.col(s"${configuration.unhaltedCycles.toLowerCase().replace('_', '-').replace(':', '-')}").toMat.col(0)
        var refCycles = frame.col(s"${configuration.refCycles.toLowerCase().replace('_', '-').replace(':', '-')}").toMat.col(0)

        for (index <- part until part + nbSteps) {
          val partFrame = CsvParser.parse(CsvFile(s"$processingPath/$frequency/$index.csv")).withRowIndex(0).withColIndex(0).mapValues(CsvParser.parseDouble)
          unhaltedCycles = unhaltedCycles.concat(partFrame.col(s"${configuration.unhaltedCycles.toLowerCase().replace('_', '-').replace(':', '-')}").toMat.col(0))
          refCycles = refCycles.concat(partFrame.col(s"${configuration.refCycles.toLowerCase().replace('_', '-').replace(':', '-')}").toMat.col(0))
        }

        math.round((unhaltedCycles / refCycles).median).toDouble
      }

      /**
        * Compute the core power in order to remove this part of consumption when is necessary.
        */
      val maxCoefficient = formulae.keys.max
      val maxUnhaltedCycles = (for (path <- (Path(s"$processingPath/${(maxCoefficient * configuration.baseFrequency * 1e6).toLong}", '/') ** "*.csv").toList) yield {
        val partFrame = CsvParser.parse(CsvFile(path.path)).withRowIndex(0).withColIndex(0).mapValues(CsvParser.parseDouble)
        partFrame.col(s"${configuration.unhaltedCycles.toLowerCase().replace('_', '-').replace(':', '-')}").toMat.col(0).median
      }).max
      val maxFormula = Array(0.0) ++ formulae(maxCoefficient).tail
      val maxCorePower = maxFormula.zipWithIndex.foldLeft(0d)((acc, elt) => acc + maxFormula(elt._2) * math.pow(maxUnhaltedCycles, elt._2))
      val partFrame = CsvParser.parse(CsvFile(s"$processingPath/$frequency/0.csv")).withRowIndex(0).withColIndex(0).mapValues(CsvParser.parseDouble)
      val idlePower = partFrame.col(s"${configuration.powers.toLowerCase().replace('_', '-').replace(':', '-')}").toMat.col(0).median
      val idleUnhaltedCycles = partFrame.col(s"${configuration.unhaltedCycles.toLowerCase().replace('_', '-').replace(':', '-')}").toMat.col(0).median

      for (part <- 1 until(parts.size, nbSteps)) {
        val coefficient = coefficients(part / nbSteps)

        if (!formulae.contains(coefficient)) {
          var x = Vec[Double](idleUnhaltedCycles)
          var y = Vec[Double](idlePower)

          for (index <- part until part + nbSteps) {
            val partFrame = CsvParser.parse(CsvFile(s"$processingPath/$frequency/$index.csv")).withRowIndex(0).withColIndex(0).mapValues(CsvParser.parseDouble)
            val power = partFrame.col(s"${configuration.powers.toLowerCase().replace('_', '-').replace(':', '-')}").toMat.col(0).median - maxCorePower * ((part - 1) / nbSteps)
            val unhaltedCycles = partFrame.col(s"${configuration.unhaltedCycles.toLowerCase().replace('_', '-').replace(':', '-')}").toMat.col(0).median - maxUnhaltedCycles * ((part - 1) / nbSteps)
            x = x.concat(Vec(unhaltedCycles))
            y = y.concat(Vec(power))
          }

          formulae += coefficient -> compute(Mat(x, y))
        }
      }
    }

    Path(computingPath, '/').deleteRecursively(force = true)
    Path(computingPath, '/').createDirectory()
    var lines = List[String]("powerapi.libpfm.formulae.cycles = [")

    for (coefficient <- formulae.keys.toList.sorted) {
      lines :+= s"  { coefficient = $coefficient, formula = [${formulae(coefficient).mkString(",")}] }"
    }

    lines :+= "]"

    (Path(computingPath, '/') /("libpfm-formula.conf", '/')).writeStrings(lines, "\n")
    val end = System.currentTimeMillis()

    log.info("Regression duration: {}", configuration.formatter.print(new Period(end - begin)))
  }

  private def compute(data: Mat[Double]): Array[Double] = {
    val unhaltedCycles = data.col(0)
    val powers = data.col(1)

    /**
      * Compute the xi^j sum (1 to degree + degree) and create the line with the maximum number of values.
      * It allows to not compute the same xi^j many times in the matrix construction.
      */
    val line = unhaltedCycles.length.toDouble +: (for (j <- 1 to degree * 2) yield unhaltedCycles.map(xi => math.pow(xi, j)).sum)

    /**
      * Take the right values into line by playing with the intervals.
      */
    val a = (for (j <- 0 to degree) yield line.slice(j, degree + j + 1)).flatten.toArray
    val A = new DenseMatrix64F(degree + 1, a.length / (degree + 1), true, a: _*)
    CommonOps.invert(A)
    val invertedA = Mat(A.getNumRows, A.getNumCols, A.getData)

    val b = (powers.sum +: (for (j <- 1 to degree) yield (unhaltedCycles.map(xi => math.pow(xi, j)) * powers).sum)).toArray
    val B = Vec(b)

    val results = (invertedA dot B).toVec

    /**
      * Error computations for logging
      *
      * @see http://www.stat.purdue.edu/~xuanyaoh/stat350/xyApr6Lec26.pdf
      */
    lazy val estimatedPowers = for (xi <- unhaltedCycles) yield results.at(0).toDouble + results.at(1).toDouble * xi + results.at(2).toDouble * xi * xi
    lazy val sst: Double = ((powers - powers.mean) ** 2).sum
    lazy val sse: Double = ((powers - estimatedPowers) ** 2).sum
    lazy val rsquared: Double = 1 - (sse / sst)
    lazy val mse: Double = sse / unhaltedCycles.length
    lazy val se: Double = math.sqrt(mse)

    log.debug(s"r^2: $rsquared; mean squared error: $mse; standard deviation: $se")
    Array(results.at(0), results.at(1), results.at(2))
  }
}

object PolynomialCyclesRegression {

  def apply(processingPath: String, computingPath: String, configuration: PolynomCyclesConfiguration): PolynomialCyclesRegression = {
    new PolynomialCyclesRegression(processingPath, computingPath, configuration)
  }
}
