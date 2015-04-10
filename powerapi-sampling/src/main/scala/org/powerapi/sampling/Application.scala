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

import scala.sys

/**
 * Main application.
 * This application has to be used with the bash script generated, not in console.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
object Application extends App {

  val shutdownHookThread = scala.sys.ShutdownHookThread {
    println("It's the time for sleeping! ...")

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
  }

  lazy val configuration = new SamplingConfiguration
  lazy val regression = new PolynomialRegression

  def printHelp(): Unit = {
    val str =
      """
        |PowerAPI, Spirals Team
        |
        |Infers the CPU power model. You have to be a sudoer to run this program.
        |Do not forget to configure correctly the modules (see the documentation).
        |
        |usage: sudo ./bin/sampling --[all|processing [sampling-path]]
      """.stripMargin

    println(str)
  }

  def cli(options: Map[Symbol, Any], args: List[String]): Map[Symbol, Any] = args match {
    case Nil => options
    case "--all" :: Nil => cli(options + ('sampling -> true, 'processing -> configuration.samplingDir), Nil)
    case "--processing" :: value :: Nil => cli(options + ('sampling -> false, 'processing -> value), Nil)
    case option :: tail => println(s"unknown cli option $option"); sys.exit(1)
  }

  if(args.size == 0) {
    printHelp()
    sys.exit(1)
  }

  val options = cli(Map(), args.toList)

  if(options('sampling).asInstanceOf[Boolean]) {
    Sampling(configuration).run()
  }

  Processing(options('processing).toString(), configuration, regression).run()

  shutdownHookThread.start()
  shutdownHookThread.join()
  shutdownHookThread.remove()
  sys.exit(0)
}
