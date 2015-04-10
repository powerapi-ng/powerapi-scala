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

import org.apache.logging.log4j.LogManager
import org.joda.time.Period
import org.saddle.{Vec, Frame, Mat}
import org.saddle.io.CsvImplicits.frame2CsvWriter
import scalax.file.Path
import scalax.file.PathMatcher.IsDirectory
import scalax.io.LongTraversable

/**
 * Process the data from the sampling directory and write the resulting csv files inside a directory.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class Processing(path: String, configuration: SamplingConfiguration, regression: PolynomialRegression)  {
  private val log = LogManager.getLogger

  def run(): Unit = {
    val begin = System.currentTimeMillis()

    val maxCoefficient = configuration.maxFrequency.toDouble / configuration.baseFrequency

    // Freq -> List[Vec(data)]
    var powerData = Map[Long, List[Vec[Double]]]()
    var unhaltedCycleData = Map[Long, List[Vec[Double]]]()
    var refCycleData = Map[Long, List[Vec[Double]]]()

    var data = Map[Double, Array[Double]]()
    var formulae = Map[Double, Array[Double]]()

    /**
     * Process sample files, keep the data in memory.
     */
    for(samplePath <- Path(path, '/') ** IsDirectory) {
      for(frequencyPath <- samplePath ** IsDirectory) {
        val frequency = frequencyPath.name.toLong
        var powerLines = (frequencyPath / configuration.outputPowers).lines()
        var unhaltedCycleLines = (frequencyPath / configuration.outputUnhaltedCycles).lines()
        var refCycleLines = (frequencyPath / configuration.outputRefCycles).lines()

        if(!powerData.contains(frequency)) {
          powerData += frequency -> List()
        }
        if(!unhaltedCycleData.contains(frequency)) {
          unhaltedCycleData += frequency -> List()
        }
        if(!refCycleData.contains(frequency)) {
          refCycleData += frequency -> List()
        }

        var index = 0
        while(powerLines.nonEmpty) {
          val powersSubset = powerLines.takeWhile(_ != configuration.separator)

          powerData += frequency -> (powerData.get(frequency) match {
            case Some(list) => list.lift(index) match {
              case Some(vector) => list.updated(index, vector.concat(Vec[Double](powersSubset.filter(_ != "").map(_.toDouble).toList: _*)))
              case _ => list :+ Vec(powersSubset.filter(_ != "").map(_.toDouble).toList: _*)
            }
            case _ => List(Vec(powersSubset.filter(_ != "").map(_.toDouble).toList: _*))
          })

          powerLines = powerLines.dropWhile(_ != configuration.separator) match {
            case traversable if traversable.size > 1 => traversable.tail
            case _ => LongTraversable[String]()
          }

          index += 1
        }

        index = 0
        while(unhaltedCycleLines.nonEmpty) {
          val unhaltedCyclesSubset = unhaltedCycleLines.takeWhile(_ != configuration.separator)

          unhaltedCycleData += frequency -> (unhaltedCycleData.get(frequency) match {
            case Some(list) => list.lift(index) match {
              case Some(vector) => list.updated(index, vector.concat(Vec[Double](unhaltedCyclesSubset.filter(_ != "").map(_.toDouble).toList: _*)))
              case _ => list :+ Vec(unhaltedCyclesSubset.filter(_ != "").map(_.toDouble).toList: _*)
            }
            case _ => List(Vec(unhaltedCyclesSubset.filter(_ != "").map(_.toDouble).toList: _*))
          })

          unhaltedCycleLines = unhaltedCycleLines.dropWhile(_ != configuration.separator) match {
            case traversable if traversable.size > 1 => traversable.tail
            case _ => LongTraversable[String]()
          }

          index += 1
        }

        index = 0
        while(refCycleLines.nonEmpty) {
          val refCyclesSubset = refCycleLines.takeWhile(_ != configuration.separator)

          refCycleData += frequency -> (refCycleData.get(frequency) match {
            case Some(list) => list.lift(index) match {
              case Some(vector) => list.updated(index, vector.concat(Vec[Double](refCyclesSubset.filter(_ != "").map(_.toDouble).toList: _*)))
              case _ => list :+ Vec(refCyclesSubset.filter(_ != "").map(_.toDouble).toList: _*)
            }
            case _ => List(Vec(refCyclesSubset.filter(_ != "").map(_.toDouble).toList: _*))
          })

          refCycleLines = refCycleLines.dropWhile(_ != configuration.separator) match {
            case traversable if traversable.size > 1 => traversable.tail
            case _ => LongTraversable[String]()
          }

          index += 1
        }
      }
    }

    /**
     * Classify the data.
     * There is a special processing for the turbo frequencies.
     */
    // Frequencies in KHz
    val frequenciesNoTB = powerData.keys.filter { frequency => frequency.toDouble / (configuration.baseFrequency * 1E6) <= maxCoefficient }
    val nbSteps = configuration.steps.size * configuration.topology.head._2.size

    for(frequency <- frequenciesNoTB) {
      val coefficient = frequency.toDouble / (configuration.baseFrequency * 1E6)

      if(unhaltedCycleData(frequency).size == powerData(frequency).size && unhaltedCycleData(frequency).size == nbSteps + 1) {
        for(i <- 0 until powerData(frequency).size) {
          val power = powerData(frequency)(i).median
          val unhaltedCycle = unhaltedCycleData(frequency)(i).median
          data += coefficient -> (data.getOrElse(coefficient, Array[Double]()) ++ Array(unhaltedCycle, power))
        }

        formulae += (coefficient -> regression.compute(Mat(data(coefficient).size / 2, 2, data(coefficient))))
      }

      else log.error("The sampling was wrong for the frequency: {}, coefficient: {}", s"$frequency", s"$coefficient")
    }

    if(configuration.turbo) {
      val frequency = (configuration.maxFrequency * 1E6).toLong + 1000

      if(powerData(frequency).size == unhaltedCycleData(frequency).size && powerData(frequency).size == refCycleData(frequency).size
        && unhaltedCycleData(frequency).size == refCycleData(frequency).size && powerData(frequency).size == nbSteps * configuration.topology.keys.size + 1) {

        val coefficients = for(i <- 1 until (unhaltedCycleData(frequency).size, nbSteps)) yield {
          math.round((unhaltedCycleData(frequency).slice(i, i + nbSteps).foldLeft(Vec[Double]())((acc, elt) => acc.concat(elt)) / refCycleData(frequency).slice(i, i + nbSteps).foldLeft(Vec[Double]())((acc, elt) => acc.concat(elt))).median).toDouble
        }

        val maxCoefficient = formulae.keys.max
        lazy val matrix = Mat(data(maxCoefficient).size / 2, 2, data(maxCoefficient))
        val maxFormula = Array(0.0) ++ formulae(maxCoefficient).tail
        lazy val maxCorePower = maxFormula.zipWithIndex.foldLeft(0d)((acc, elt) => acc + maxFormula(elt._2) * math.pow(matrix.col(0).max.get, elt._2))

        val idlePower = powerData(frequency)(0).median
        val idleUnhaltedCycles = unhaltedCycleData(frequency)(0).median
        powerData += frequency -> powerData(frequency).tail
        unhaltedCycleData += frequency -> unhaltedCycleData(frequency).tail
        refCycleData += frequency -> refCycleData(frequency).tail

        for(i <- 0 until (powerData(frequency).size, nbSteps)) {
          val coefficient = coefficients(i / nbSteps)

          if(!data.contains(coefficient)) {
            data += coefficient -> (data.getOrElse(coefficient, Array[Double]()) ++ Array(idleUnhaltedCycles, idlePower))

            for(j <- i until i + nbSteps) {
              val power = powerData(frequency)(j).median - maxCorePower * (i / nbSteps)
              val unhaltedCycle = unhaltedCycleData(frequency)(j).median - matrix.col(0).max.get * (i / nbSteps)

              data += coefficient -> (data.getOrElse(coefficient, Array[Double]()) ++ Array(unhaltedCycle, power))
            }

            formulae += (coefficient -> regression.compute(Mat(data(coefficient).size / 2, 2, data(coefficient))))
          }
        }
      }

      else log.error("The sampling was wrong for the turbo frequencies.")
    }

    if(Path(path, '/').exists) {
      Path(configuration.processingDir, '/').deleteRecursively(force = true)
      Path(configuration.processingDir, '/').createDirectory()

      for((coefficient, values) <- data) {
        val matrix = Mat(values.size / 2, 2, values)
        Frame("unhalted-cycles" -> matrix.col(0), "P" -> matrix.col(1)).writeCsvFile(s"${configuration.processingDir}/$coefficient.csv")
      }
    }

    if(Path(configuration.processingDir, '/').exists) {
      Path(configuration.computingDir, '/').deleteRecursively(force = true)
      Path(configuration.computingDir, '/').createDirectory()
      var lines = List[String]("powerapi.libpfm.formulae.cycles = [")

      for(coefficient <- formulae.keys.toList.sorted) {
        lines :+= s"  { coefficient = $coefficient, formula = [${formulae(coefficient).mkString(",")}] }"
      }

      lines :+= "]"

      (Path(configuration.computingDir, '/') / ("libpfm-formula.conf", '/')).writeStrings(lines, "\n")
    }

    val end = System.currentTimeMillis()
    log.info("Processing duration: {}", configuration.formatter.print(new Period(end - begin)))
  }
}

object Processing extends SamplingConfiguration {
  def apply(path: String, configuration: SamplingConfiguration, regression: PolynomialRegression): Processing = {
    new Processing(path, configuration, regression)
  }
}
