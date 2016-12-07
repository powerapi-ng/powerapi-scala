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

import com.typesafe.scalalogging.Logger
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.{DirectoryFileFilter, SuffixFileFilter}
import org.joda.time.Period

import scala.io.Source

/**
  * Process the data from the sampling directory and write the resulting csv files inside a directory.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class Processing(samplingPath: String, processingPath: String, configuration: SamplingConfiguration) {
  private val log = Logger(classOf[Processing])

  def run(): Unit = {
    val begin = System.currentTimeMillis()

    try {
      FileUtils.deleteDirectory(new File(s"$processingPath"))
      FileUtils.forceMkdir(new File(s"$processingPath"))
    }
    catch {
      case ex: Exception =>
        log.error(s"Failure: ${ex.getMessage}")
    }

    val frequencies = scala.collection.mutable.Set[Long]()
    val data = scala.collection.mutable.Map[(Long, String), List[List[Double]]]()

    /**
      * Process sample files, keep the data in memory.
      */
    for (sampleDir <- new File(s"$samplingPath").list(DirectoryFileFilter.INSTANCE)) {
      for (frequencyDir <- new File(s"$samplingPath/$sampleDir").list(DirectoryFileFilter.INSTANCE)) {
        val frequency = frequencyDir.toLong
        frequencies += frequency

        for (eventFile <- new File(s"$samplingPath/$sampleDir/$frequencyDir").list(new SuffixFileFilter(".dat"))) {
          val event = eventFile.replace(configuration.baseOutput, "").replace(".dat", "")

          if (!data.contains((frequency, event))) {
            data += (frequency, event) -> List()
          }

          var lines = Source.fromFile(s"$samplingPath/$sampleDir/$frequencyDir/$eventFile").getLines().toTraversable
          var index = 0

          while (lines.nonEmpty) {
            val dataSubset = lines.takeWhile(_ != configuration.separator)

            data += (frequency, event) -> (data.get((frequency, event)) match {
              case Some(list) => list.lift(index) match {
                case Some(vector) => list.updated(index, vector ::: List[Double](dataSubset.filter(_ != "").map(_.toDouble).toList: _*))
                case _ => list :+ List(dataSubset.filter(_ != "").map(_.toDouble).toList: _*)
              }
              case _ => List(List(dataSubset.filter(_ != "").map(_.toDouble).toList: _*))
            })

            lines = lines.dropWhile(_ != configuration.separator) match {
              case traversable if traversable.size > 1 => traversable.tail
              case _ => Traversable[String]()
            }

            index += 1
          }
        }
      }
    }

    for (frequency <- frequencies) {
      FileUtils.forceMkdir(new File(s"$processingPath/$frequency"))

      val dataPerFreq = data.filter(_._1._1 == frequency)
      val size = dataPerFreq.values.head.size

      if (dataPerFreq.values.count(list => list.size != size) == 0) {
        for (index <- 0 until size) {
          val dataStep = scala.collection.mutable.Map[String, List[Double]]()
          val min = (for (elt <- dataPerFreq) yield elt._2(index).length).min

          val events = dataPerFreq.keys.map(_._2).toList.sorted
          val lines = scala.collection.mutable.ArrayBuffer[String](s"${events.mkString(",")}")

          for (event <- events) {
            val values = dataPerFreq(frequency, event)(index)

            for (i <- 0 until values.length) {
              if (i + 1 > lines.size - 1) lines += ""
              lines(i + 1) = if (lines(i + 1) != "") s"${lines(i + 1)},${values(i)}" else s"${values(i)}"
            }
          }

          lines.foreach(line => FileUtils.write(new File(s"$processingPath/$frequency/$index.csv"), s"$line\n", "UTF-8", true))
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
