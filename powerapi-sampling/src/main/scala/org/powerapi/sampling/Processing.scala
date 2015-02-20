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

import org.powerapi.core.Configuration

/**
 * Process the data from the sampling directory and write the resulting csv files inside a directory.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class Processing(samplingDir: String, processingDir: String, separator: String, outputPowers: String, outputUnhaltedCycles: String, outputRefCycles: String) extends Configuration {
  import org.apache.logging.log4j.LogManager
  import org.powerapi.core.ConfigValue

  private val log = LogManager.getLogger

  lazy val baseFrequency: Double = load { _.getDouble("powerapi.sampling.cpu-base-frequency") } match {
    case ConfigValue(value) => value
    case _ => 0d
  }

  lazy val maxFrequency: Double = load { _.getDouble("powerapi.sampling.cpu-max-frequency") } match {
    case ConfigValue(value) => value
    case _ => 0d
  }

  def run(): Unit = {
    import org.saddle.{Frame, Mat, Vec}
    import org.saddle.io.CsvImplicits.frame2CsvWriter
    import scalax.io.LongTraversable
    import scalax.file.Path
    import scalax.file.PathMatcher.IsDirectory

    val maxCoefficient = maxFrequency.toDouble / baseFrequency
    var frequencies = Set[Long]()

    // Freq -> List[Vec(data)]
    var powerData = Map[Long, List[Vec[Double]]]()
    var unhaltedCycleData = Map[Long, List[Vec[Double]]]()
    var refCycleData = Map[Long, List[Vec[Double]]]()

    var data = Map[Double, Array[Double]]()

    /**
     * Process sample files, keep the data in memory.
     */
    for(samplePath <- Path(samplingDir, '/') ** IsDirectory) {
      for(frequencyPath <- samplePath ** IsDirectory) {
        val frequency = frequencyPath.name.toLong
        var powerLines = (frequencyPath / outputPowers).lines()
        var unhaltedCycleLines = (frequencyPath / outputUnhaltedCycles).lines()
        var refCycleLines = (frequencyPath / outputRefCycles).lines()

        frequencies += frequency

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
          val powersSubset = powerLines.takeWhile(_ != separator)

          powerData += frequency -> (powerData.get(frequency) match {
            case Some(list) => list.lift(index) match {
              case Some(vector) => list.updated(index, vector.concat(Vec[Double](powersSubset.filter(_ != "").map(_.toDouble).toList: _*)))
              case _ => list :+ Vec(powersSubset.filter(_ != "").map(_.toDouble).toList: _*)
            }
            case _ => List(Vec(powersSubset.filter(_ != "").map(_.toDouble).toList: _*))
          })

          powerLines = powerLines.dropWhile(_ != separator) match {
            case traversable if traversable.size > 1 => traversable.tail
            case _ => LongTraversable[String]()
          }

          index += 1
        }

        index = 0
        while(unhaltedCycleLines.nonEmpty) {
          val unhaltedCyclesSubset = unhaltedCycleLines.takeWhile(_ != separator)

          unhaltedCycleData += frequency -> (unhaltedCycleData.get(frequency) match {
            case Some(list) => list.lift(index) match {
              case Some(vector) => list.updated(index, vector.concat(Vec[Double](unhaltedCyclesSubset.filter(_ != "").map(_.toDouble).toList: _*)))
              case _ => list :+ Vec(unhaltedCyclesSubset.filter(_ != "").map(_.toDouble).toList: _*)
            }
            case _ => List(Vec(unhaltedCyclesSubset.filter(_ != "").map(_.toDouble).toList: _*))
          })

          unhaltedCycleLines = unhaltedCycleLines.dropWhile(_ != separator) match {
            case traversable if traversable.size > 1 => traversable.tail
            case _ => LongTraversable[String]()
          }

          index += 1
        }

        index = 0
        while(refCycleLines.nonEmpty) {
          val refCyclesSubset = refCycleLines.takeWhile(_ != separator)

          refCycleData += frequency -> (refCycleData.get(frequency) match {
            case Some(list) => list.lift(index) match {
              case Some(vector) => list.updated(index, vector.concat(Vec[Double](refCyclesSubset.filter(_ != "").map(_.toDouble).toList: _*)))
              case _ => list :+ Vec(refCyclesSubset.filter(_ != "").map(_.toDouble).toList: _*)
            }
            case _ => List(Vec(refCyclesSubset.filter(_ != "").map(_.toDouble).toList: _*))
          })

          refCycleLines = refCycleLines.dropWhile(_ != separator) match {
            case traversable if traversable.size > 1 => traversable.tail
            case _ => LongTraversable[String]()
          }

          index += 1
        }
      }
    }

    /**
     * Classify the data with the coefficients
     */
    for(frequency <- frequencies) {
      // Frequencies in KHz
      val coefficient = frequency.toDouble / (baseFrequency * 1E6)

      if(unhaltedCycleData(frequency).size == powerData(frequency).size && refCycleData(frequency).size == powerData(frequency).size) {
        if(coefficient <= maxCoefficient) data += coefficient -> Array()

        for(i <- 0 until powerData(frequency).size) {
          val power = powerData(frequency)(i).median
          val unhaltedCycles = unhaltedCycleData(frequency)(i).median
          val refCycles = refCycleData(frequency)(i).median
          val coefficient = math.round(unhaltedCycles / refCycles).toDouble

          // Frequencies before boost mode
          if(coefficient <= maxCoefficient) {
            if(data.contains(coefficient)) {
              data += coefficient -> (data(coefficient) ++ Array(unhaltedCycles, power))
            }
            else {
              val coefficientsBefore = data.keys.filter(_ < coefficient)
              if(coefficientsBefore.size > 0) {
                data += coefficientsBefore.max -> (data(coefficientsBefore.max) ++ Array(unhaltedCycles, power))
              }
            }
          }
          // Boost mode
          else {
            data += coefficient -> (data.getOrElse(coefficient, Array()) ++ Array(unhaltedCycles, power))
          }
        }
      }

      else log.error("The sampling was wrong for the frequency: {}, coefficient: {}", s"$frequency", s"$coefficient")
    }

    if(Path(samplingDir, '/').exists) {
      Path(processingDir, '/').deleteRecursively(force = true)
      Path(processingDir, '/').createDirectory()

      for((coefficient, values) <- data) {
        val matrix = Mat(values.size / 2, 2, values)
        Frame("unhalted-cycles" -> matrix.col(0), "P" -> matrix.col(1)).writeCsvFile(s"$processingDir/$coefficient.csv")
      }
    }
  }
}

object Processing {
  def apply(samplingDir: String, processingDir: String, separator: String, outputPowers: String, outputUnhaltedCycles: String, outputRefCycles: String): Processing = {
    new Processing(samplingDir, processingDir, separator, outputPowers, outputUnhaltedCycles, outputRefCycles)
  }
}
