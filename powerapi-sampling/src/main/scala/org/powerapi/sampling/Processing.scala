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
import org.saddle.{Vec, Frame}
import org.saddle.io.CsvImplicits.frame2CsvWriter
import scalax.file.Path
import scalax.file.PathMatcher.IsDirectory
import scalax.io.LongTraversable

/**
 * Process the data from the sampling directory and write the resulting csv files inside a directory.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class Processing(samplingPath: String, processingPath: String, configuration: SamplingConfiguration) {
  private val log = LogManager.getLogger

  def run(): Unit = {
    val begin = System.currentTimeMillis()

    Path(processingPath, '/').deleteRecursively(force = true)
    Path(processingPath, '/').createDirectory()

    val frequencies = scala.collection.mutable.Set[Long]()
    val data = scala.collection.mutable.Map[(Long, String), List[Vec[Double]]]()

    /**
     * Process sample files, keep the data in memory.
     */
    for(samplePath <- Path(samplingPath, '/') ** IsDirectory) {
      for (frequencyPath <- samplePath ** IsDirectory) {
        val frequency = frequencyPath.name.toLong
        frequencies += frequency

        for(eventPath <- frequencyPath ** "*.dat") {
          val event = eventPath.name.replace(configuration.baseOutput, "").replace(".dat", "")

          if (!data.contains(frequency, event)) {
            data += (frequency, event) -> List()
          }

          var lines = eventPath.lines()
          var index = 0

          while(lines.nonEmpty) {
            val dataSubset = lines.takeWhile(_ != configuration.separator)

            data += (frequency, event) -> (data.get(frequency, event) match {
              case Some(list) => list.lift(index) match {
                case Some(vector) => list.updated(index, vector.concat(Vec[Double](dataSubset.filter(_ != "").map(_.toDouble).toList: _*)))
                case _ => list :+ Vec(dataSubset.filter(_ != "").map(_.toDouble).toList: _*)
              }
              case _ => List(Vec(dataSubset.filter(_ != "").map(_.toDouble).toList: _*))
            })

            lines = lines.dropWhile(_ != configuration.separator) match {
              case traversable if traversable.size > 1 => traversable.tail
              case _ => LongTraversable[String]()
            }

            index += 1
          }
        }
      }
    }

    for(frequency <- frequencies) {
      Path(s"$processingPath/$frequency", '/').createDirectory()

      val dataPerFreq = data.filter(_._1._1 == frequency)
      val size = dataPerFreq.values.head.size

      if(dataPerFreq.values.count(list => list.size != size) == 0) {
        for (index <- 0 until size) {
          val dataStep = scala.collection.mutable.ListBuffer[(String, Vec[Double])]()
          val min = (for (elt <- dataPerFreq) yield elt._2(index).length).min

          for (((_, event), values) <- dataPerFreq) {
            dataStep += event -> values(index).slice(0, min)
          }

          Frame(dataStep: _*).writeCsvFile(s"$processingPath/$frequency/$index.csv")
        }
      }

      else log.error("The sampling was wrong for the frequency: {}", s"$frequency")
    }

    val end = System.currentTimeMillis()
    log.info("Processing duration: {}", configuration.formatter.print(new Period(end - begin)))
  }
}

object Processing extends SamplingConfiguration {
  def apply(samplingPath: String, processingPath: String, configuration: SamplingConfiguration): Processing = {
    new Processing(samplingPath, processingPath, configuration)
  }
}
