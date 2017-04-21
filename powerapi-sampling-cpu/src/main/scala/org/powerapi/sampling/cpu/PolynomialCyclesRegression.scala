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

import com.twitter.util.{Await, Duration}
import com.twitter.zk.ZNode
import com.typesafe.scalalogging.Logger
import org.apache.commons.io.filefilter.SuffixFileFilter
import org.ejml.data.DenseMatrix64F
import org.ejml.ops.CommonOps
import org.joda.time.Period
import grizzled.math.stats._

import Numeric._
import scala.io.Source

/**
  * Compute the CPU formulae by using the unhalted and the reference cycles.
  * The resulting formula is a formula of degree 2.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class PolynomialCyclesRegression(processingPath: String, zNode: ZNode, zkTimeout: Int, configuration: PolynomCyclesConfiguration) extends Regression {

  private val log = Logger(classOf[PolynomialCyclesRegression])
  private val degree = 2

  def run(): Unit = {
    val begin = System.currentTimeMillis()

    /*val maxCoefficient = (configuration.maxFrequency.toDouble * 1e6) / (configuration.baseFrequency * 1e6)
    val frequencies = new File(processingPath).list(DirectoryFileFilter.INSTANCE).map(_.toLong)
    val frequenciesNoTB = frequencies.filter { frequency => frequency.toDouble / (configuration.baseFrequency * 1e6) <= maxCoefficient }
    val nbSteps = configuration.steps.size * configuration.topology.head._2.size*/
    val formulae = scala.collection.mutable.Map[Double, Array[Double]]()

    for (frequency <- Seq(0)) {
      val coefficient = 0d
      val parts = new File(s"$processingPath/$frequency").list(new SuffixFileFilter(".csv")).map(_.replace(".csv", "").toLong).sorted
      val x = collection.mutable.ListBuffer[Double]()
      val y = collection.mutable.ListBuffer[Double]()

      for (part <- parts) {
        val csv = for (line <- Source.fromFile(s"$processingPath/$frequency/$part.csv").getLines().toArray) yield line.split(",").toList
        val csvData = scala.collection.mutable.SortedMap[String, scala.collection.mutable.ArrayBuffer[Double]]() ++ csv.head.map(header => (header, scala.collection.mutable.ArrayBuffer[Double]())).toMap
        for (data <- csv.tail) {
          for (i <- 0 until data.size) {
            csvData(csvData.keys.toArray.apply(i)) += data(i).toDouble
          }
        }
        x += median(csvData(s"${configuration.unhaltedCycles.toLowerCase().replace('_', '-').replace(':', '-')}"): _*)
        y += median(csvData(s"${configuration.powers.toLowerCase().replace('_', '-').replace(':', '-')}"): _*)
      }

      formulae += coefficient -> compute(Map(s"${configuration.unhaltedCycles.toLowerCase().replace('_', '-').replace(':', '-')}" -> x, s"${configuration.powers.toLowerCase().replace('_', '-').replace(':', '-')}" -> y))
    }

    Await.result(zNode.setData(new String(formulae(0).mkString(",")).getBytes, 0), Duration.fromSeconds(zkTimeout))
    //Await.result(zNode.setData(new String(formulae(0).mkString(",")).getBytes, 0), Duration.fromSeconds(zkTimeout))

    val end = System.currentTimeMillis()

    log.info("Regression duration: {}", configuration.formatter.print(new Period(end - begin)))

    /**
      * We apply a specific algorithm for the turbo frequencies.
      * We try to infer the different coefficients by stressing the processor core by core to activate or not the heuristics.
      * Given the fact we cannot get the "true" power consumption for a core, we subtract the estimated one when it's necessary.
      */
    /*if (configuration.turbo) {
      val frequency = (configuration.maxFrequency * 1E6).toLong + 1000
      val parts = new File(s"$processingPath/$frequency").list(new SuffixFileFilter(".csv")).map(_.replace(".csv", "").toLong).sorted
      val coefficients = for (part <- 1 until(parts.size, nbSteps)) yield {
        val csv = for (line <- Source.fromFile(s"$processingPath/$frequency/$part.csv").getLines().toArray) yield line.split(",")
        val csvData = scala.collection.mutable.SortedMap[String, scala.collection.mutable.ArrayBuffer[Double]]() ++ csv.head.map(header => (header, scala.collection.mutable.ArrayBuffer[Double]())).toMap
        for (data <- csv.tail) {
          for (i <- 0 until data.size) {
            csvData(csvData.keys.toArray.apply(i)) += data(i).toDouble
          }
        }

        var unhaltedCycles = csvData(s"${configuration.unhaltedCycles.toLowerCase().replace('_', '-').replace(':', '-')}")
        var refCycles = csvData(s"${configuration.refCycles.toLowerCase().replace('_', '-').replace(':', '-')}")

        for (index <- part until part + nbSteps) {
          val csv = for (line <- Source.fromFile(s"$processingPath/$frequency/$index.csv").getLines().toArray) yield line.split(",")
          val csvData = scala.collection.mutable.SortedMap[String, scala.collection.mutable.ArrayBuffer[Double]]() ++ csv.head.map(header => (header, scala.collection.mutable.ArrayBuffer[Double]())).toMap
          for (data <- csv.tail) {
            for (i <- 0 until data.size) {
              csvData(csvData.keys.toArray.apply(i)) += data(i).toDouble
            }
          }

          unhaltedCycles ++= csvData(s"${configuration.unhaltedCycles.toLowerCase().replace('_', '-').replace(':', '-')}")
          refCycles ++= csvData(s"${configuration.refCycles.toLowerCase().replace('_', '-').replace(':', '-')}")
        }

        math.round(median((for (i <- 0 until unhaltedCycles.length) yield unhaltedCycles(i) / refCycles(i)): _*)).toDouble
      }

      /**
        * Compute the core power in order to remove this part of consumption when is necessary.
        */
      val maxCoefficient = formulae.keys.max
      val maxUnhaltedCycles = (for (file <- new File(s"$processingPath/${(maxCoefficient * configuration.baseFrequency * 1e6).toLong}").list(new SuffixFileFilter(".csv"))) yield {
        val csv = for (line <- Source.fromFile(s"$processingPath/${(maxCoefficient * configuration.baseFrequency * 1e6).toLong}/$file").getLines().toArray) yield line.split(",")
        val csvData = scala.collection.mutable.SortedMap[String, scala.collection.mutable.ArrayBuffer[Double]]() ++ csv.head.map(header => (header, scala.collection.mutable.ArrayBuffer[Double]())).toMap
        for (data <- csv.tail) {
          for (i <- 0 until data.size) {
            csvData(csvData.keys.toArray.apply(i)) += data(i).toDouble
          }
        }
        median(csvData(s"${configuration.unhaltedCycles.toLowerCase().replace('_', '-').replace(':', '-')}"): _*)
      }).max

      val maxFormula = Array(0.0) ++ formulae(maxCoefficient).tail
      val maxCorePower = maxFormula.zipWithIndex.foldLeft(0d)((acc, elt) => acc + maxFormula(elt._2) * math.pow(maxUnhaltedCycles, elt._2))

      val csv = for (line <- Source.fromFile(s"$processingPath/$frequency/0.csv").getLines().toArray) yield line.split(",")
      val csvData = scala.collection.mutable.SortedMap[String, scala.collection.mutable.ArrayBuffer[Double]]() ++ csv.head.map(header => (header, scala.collection.mutable.ArrayBuffer[Double]())).toMap
      for (data <- csv.tail) {
        for (i <- 0 until data.size) {
          csvData(csvData.keys.toArray.apply(i)) += data(i).toDouble
        }
      }
      val idlePower = median(csvData(s"${configuration.powers.toLowerCase().replace('_', '-').replace(':', '-')}"): _*)
      val idleUnhaltedCycles = median(csvData(s"${configuration.unhaltedCycles.toLowerCase().replace('_', '-').replace(':', '-')}"): _*)

      for (part <- 1 until(parts.size, nbSteps)) {
        val coefficient = coefficients(part / nbSteps)

        if (!formulae.contains(coefficient)) {
          val x = collection.mutable.ListBuffer[Double](idleUnhaltedCycles)
          val y = collection.mutable.ListBuffer[Double](idlePower)

          for (index <- part until part + nbSteps) {
            val csv = for (line <- Source.fromFile(s"$processingPath/$frequency/$index.csv").getLines().toArray) yield line.split(",")
            val csvData = scala.collection.mutable.SortedMap[String, scala.collection.mutable.ArrayBuffer[Double]]() ++ csv.head.map(header => (header, scala.collection.mutable.ArrayBuffer[Double]())).toMap
            for (data <- csv.tail) {
              for (i <- 0 until data.size) {
                csvData(csvData.keys.toArray.apply(i)) += data(i).toDouble
              }
            }
            val power = median(csvData(s"${configuration.powers.toLowerCase().replace('_', '-').replace(':', '-')}"): _*) - maxCorePower * ((part - 1) / nbSteps)
            val unhaltedCycles = median(csvData(s"${configuration.unhaltedCycles.toLowerCase().replace('_', '-').replace(':', '-')}"): _*) - maxUnhaltedCycles * ((part - 1) / nbSteps)
            x += unhaltedCycles
            y += power
          }

          formulae += coefficient -> compute(Map(s"${configuration.unhaltedCycles.toLowerCase().replace('_', '-').replace(':', '-')}" -> x, s"${configuration.powers.toLowerCase().replace('_', '-').replace(':', '-')}" -> y))
        }
      }
    }*/

    /*try {
      FileUtils.deleteDirectory(new File(s"$computingPath"))
      FileUtils.forceMkdir(new File(s"$computingPath"))
    }
    catch {
      case ex: Exception =>
        log.error(s"Failure: ${ex.getMessage}")
    }

    var lines = List[String]("powerapi.hwc.formulae.cycles = [")

    for (coefficient <- formulae.keys.toList.sorted) {
      lines :+= s"  { coefficient = $coefficient, formula = [${formulae(coefficient).mkString(",")}] }"
    }

    lines :+= "]"

    FileUtils.writeLines(new File(s"$computingPath/hwc-formula.conf"), lines.asJavaCollection, "\n", true)

    val end = System.currentTimeMillis()

    log.info("Regression duration: {}", configuration.formatter.print(new Period(end - begin)))*/
  }

  private def compute(data: Map[String, Seq[Double]]): Array[Double] = {
    val unhaltedCycles = data(s"${configuration.unhaltedCycles.toLowerCase().replace('_', '-').replace(':', '-')}")
    val powers = data(s"${configuration.powers.toLowerCase().replace('_', '-').replace(':', '-')}")

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
    val b = (powers.sum +: (for (j <- 1 to degree) yield {
      val powR = unhaltedCycles.map(xi => math.pow(xi, j))
      powR.zipWithIndex.map(tuple => tuple._1 * powers(tuple._2))
    }.sum)).toArray
    val B = DenseMatrix64F.wrap(b.length, 1, b)
    val R = new DenseMatrix64F(A.numRows, 1)
    CommonOps.mult(A, B, R)
    //R.print()

    /**
      * Error computations for logging
      *
      * @see http://www.stat.purdue.edu/~xuanyaoh/stat350/xyApr6Lec26.pdf
      */
//    lazy val estimatedPowers = for (xi <- unhaltedCycles) yield results.at(0).toDouble + results.at(1).toDouble * xi + results.at(2).toDouble * xi * xi
//    lazy val sst: Double = ((powers - powers.mean) ** 2).sum
//    lazy val sse: Double = ((powers - estimatedPowers) ** 2).sum
//    lazy val rsquared: Double = 1 - (sse / sst)
//    lazy val mse: Double = sse / unhaltedCycles.length
//    lazy val se: Double = math.sqrt(mse)

//    log.debug(s"r^2: $rsquared; mean squared error: $mse; standard deviation: $se")
//    Array(results.at(0), results.at(1), results.at(2))
    Array(R.get(0, 0), R.get(1, 0), R.get(2, 0))
  }
}

object PolynomialCyclesRegression {

  def apply(processingPath: String, zNode: ZNode, zkTimeout: Int, configuration: PolynomCyclesConfiguration): PolynomialCyclesRegression = {
    new PolynomialCyclesRegression(processingPath, zNode, zkTimeout, configuration)
  }
}
