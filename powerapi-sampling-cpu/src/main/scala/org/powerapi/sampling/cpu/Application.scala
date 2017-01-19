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

import org.apache.commons.io.filefilter.PrefixFileFilter

import scala.sys
import scala.sys.process.stringSeqToProcess
import org.powerapi.module.libpfm.LibpfmHelper

/**
  * Main application.
  * This application has to be used with the bash script generated, not in console.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
object Application extends App {

  // core -> (governor, frequency)
  val backup = scala.collection.mutable.HashMap[String, (String, Option[Long])]()

  if (new File("/sys/devices/system/cpu/").exists()) {
    for (file <- new File("/sys/devices/system/cpu/").list(new PrefixFileFilter("cpu"))) {
      val governor = Seq("cat", s"/sys/devices/system/cpu/$file/cpufreq/scaling_governor").lineStream.toArray.apply(0)
      val frequency = Seq("cat", s"/sys/devices/system/cpu/$file/cpufreq/scaling_setspeed").lineStream.toArray.apply(0)

      if (frequency matches """\d+""") {
        backup += (s"/sys/devices/system/cpu/$file" -> ((governor, Some(frequency.toLong))))
      }
      else backup += (s"/sys/devices/system/cpu/$file" -> ((governor, None)))
    }
  }

  val shutdownHookThread = scala.sys.ShutdownHookThread {
    Sampling.powerapi match {
      case Some(papi) => {
        papi.shutdown()
      }
      case _ => {}
    }

    Sampling.externalPMeter match {
      case Some(ePMeter) => {
        ePMeter.shutdown()
      }
      case _ => {}
    }

    for ((path, (governor, frequency)) <- backup) {
      (Seq("echo", s"$governor") #>> new File(s"$path/cpufreq/scaling_governor")).!

      if (governor == "userspace" && frequency.isDefined) {
        (Seq("echo", s"${frequency.get}") #>> new File(s"$path/cpufreq/scaling_setspeed")).!
      }
    }
  }

  if (args.isEmpty) {
    printHelp()
    sys.exit(1)
  }

  val options = cli(Map(), args.toList)
  val samplingOption = options('sampling).asInstanceOf[(Boolean, String)]
  val processingOption = options('processing).asInstanceOf[(Boolean, String)]
  val computingOption = options('computing).asInstanceOf[(Boolean, String)]
  val libpfmHelper = new LibpfmHelper
  val configuration = new PolynomCyclesConfiguration

  def printHelp(): Unit = {
    val str =
      """
        |PowerAPI, Spirals Team.
        |
        |Infers the CPU power model. You have to run this program in sudo mode.
        |Do not forget to configure correctly the modules (see the documentation).
        |
        |usage: sudo ./bin/sampling --all [sampling-path] [processing-path] [computing-path]
        |                         ||--sampling [sampling-path]
        |                         ||--processing [sampling-path] [processing-path]
        |                         ||--computing [processing-path] [computing-path]
      """.stripMargin

    println(str)
  }

  def cli(options: Map[Symbol, Any], args: List[String]): Map[Symbol, Any] = args match {
    case Nil =>
      options
    case "--all" :: samplingPath :: processingPath :: computingPath :: Nil =>
      cli(options +('sampling -> ((true, samplingPath)), 'processing -> ((true, processingPath)), 'computing -> ((true, computingPath))), Nil)
    case "--sampling" :: samplingPath :: Nil =>
      cli(options +('sampling -> ((true, samplingPath)), 'processing -> ((false, "")), 'computing -> ((false, ""))), Nil)
    case "--processing" :: samplingPath :: processingPath :: Nil =>
      cli(options +('sampling -> ((false, samplingPath)), 'processing -> ((true, processingPath)), 'computing -> ((false, ""))), Nil)
    case "--computing" :: processingPath :: computingPath :: Nil =>
      cli(options +('sampling -> ((false, "")), 'processing -> ((false, processingPath)), 'computing -> ((true, computingPath))), Nil)
    case option :: tail =>
      println(s"unknown cli option $option"); sys.exit(1)
  }

  if (samplingOption._1) {
    Sampling(samplingOption._2, configuration, libpfmHelper).run()
  }

  if (processingOption._1) {
    Processing(samplingOption._2, processingOption._2, configuration).run()
  }

  if (computingOption._1) {
    PolynomialCyclesRegression(processingOption._2, computingOption._2, configuration).run()
  }

  shutdownHookThread.start()
  shutdownHookThread.join()
  shutdownHookThread.remove()
  sys.exit(0)
}
