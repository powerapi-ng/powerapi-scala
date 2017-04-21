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

import com.twitter.util.{Await, Duration, JavaTimer, Return}
import com.twitter.zk.{ZNode, ZkClient}
import likwid.LikwidLibrary
import org.apache.zookeeper.ZooDefs.Ids.OPEN_ACL_UNSAFE
import org.powerapi.PowerMeter
import org.powerapi.core.LinuxHelper
import org.powerapi.module.hwc.{CHelper, HWCCoreSensorModule, LikwidHelper}

import scala.sys
import scala.collection.JavaConverters._
import org.powerapi.module.rapl.RaplCpuModule

/**
  * Main application.
  * This application has to be used with the bash script generated, not in console.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
object Application extends App {

  // core -> (governor, frequency)
  /*val backup = scala.collection.mutable.HashMap[String, (String, Option[Long])]()

  if (new File("/sys/devices/system/cpu/").exists()) {
    for (file <- new File("/sys/devices/system/cpu/").list(new PrefixFileFilter("cpu"))) {
      val governor = Seq("cat", s"/sys/devices/system/cpu/$file/cpufreq/scaling_governor").lineStream.toArray.apply(0)
      val frequency = Seq("cat", s"/sys/devices/system/cpu/$file/cpufreq/scaling_setspeed").lineStream.toArray.apply(0)

      if (frequency matches """\d+""") {
        backup += (s"/sys/devices/system/cpu/$file" -> ((governor, Some(frequency.toLong))))
      }
      else backup += (s"/sys/devices/system/cpu/$file" -> ((governor, None)))
    }
  }*/

  var powerapi: Option[PowerMeter] = None
  var groundTruth: Option[PowerMeter] = None

  val shutdownHookThread = scala.sys.ShutdownHookThread {
    powerapi match {
      case Some(papi) => {
        papi.shutdown()
      }
      case _ => {}
    }

    groundTruth match {
      case Some(ePMeter) => {
        ePMeter.shutdown()
      }
      case _ => {}
    }

//    for ((path, (governor, frequency)) <- backup) {
//      (Seq("echo", s"$governor") #>> new File(s"$path/cpufreq/scaling_governor")).!
//
//      if (governor == "userspace" && frequency.isDefined) {
//        (Seq("echo", s"${frequency.get}") #>> new File(s"$path/cpufreq/scaling_setspeed")).!
//      }
//    }
  }

  if (args.isEmpty) {
    printHelp()
    sys.exit(1)
  }

//  val options = cli(Map(), args.toList)
//  val samplingOption = options('sampling).asInstanceOf[(Boolean, String)]
//  val processingOption = options('processing).asInstanceOf[(Boolean, String)]
//  val computingOption = options('computing).asInstanceOf[(Boolean, String)]
//  val raplOption = options('rapl).asInstanceOf[Boolean]
//  val powerspyOption = options('powerspy).asInstanceOf[Boolean]
//  val kwapiOption = options('kwapi).asInstanceOf[Boolean]
//  val configuration = new PolynomCyclesConfiguration

  def printHelp(): Unit = {
    val str =
      """
        |PowerAPI, Spirals Team.
        |
        |Infers the CPU power model. You have to run this program in sudo mode.
        |Do not forget to configure correctly the modules (see the documentation).
        |
        |usage: sudo ./bin/sampling --zkUrl [url] --zkTimeout [s]
      """.stripMargin

    println(str)
  }

//  def cli(options: Map[Symbol, Any], args: List[String]): Map[Symbol, Any] = args match {
//    case Nil =>
//      options
//    case "--all" :: samplingPath :: processingPath :: computingPath :: groundTruth =>
//      cli(options +('sampling -> ((true, samplingPath)), 'processing -> ((true, processingPath)), 'computing -> ((true, computingPath))), groundTruth)
//    case "--sampling" :: samplingPath :: groundTruth =>
//      cli(options +('sampling -> ((true, samplingPath)), 'processing -> ((false, "")), 'computing -> ((false, ""))), groundTruth)
//    case "--processing" :: samplingPath :: processingPath :: groundTruth =>
//      cli(options +('sampling -> ((false, samplingPath)), 'processing -> ((true, processingPath)), 'computing -> ((false, ""))), Nil)
//    case "--computing" :: processingPath :: computingPath :: groundTruth =>
//      cli(options +('sampling -> ((false, "")), 'processing -> ((false, processingPath)), 'computing -> ((true, computingPath))), Nil)
//    case "--rapl" :: Nil if options.nonEmpty =>
//      cli(options +('rapl -> true, 'powerspy -> false, 'kwapi -> false), Nil)
//    case "--powerspy" :: Nil if options.nonEmpty =>
//      cli(options +('rapl -> false, 'powerspy -> true, 'kwapi -> false), Nil)
//    case "--kwapi" :: Nil if options.nonEmpty =>
//      cli(options +('rapl -> false, 'powerspy -> false, 'kwapi -> true), Nil)
//    case option :: tail =>
//      println(s"unknown cli option $option"); sys.exit(1)
//  }

  def cli(options: Map[Symbol, Any], args: List[String]): Map[Symbol, Any] = args match {
    case Nil =>
      options
    case "--zkUrl" :: zkUrl :: tail =>
      cli(options +('zkUrl -> zkUrl), tail)
    case "--zkTimeout" :: zkTimeout :: tail =>
      cli(options +('zkTimeout -> zkTimeout), tail)
    case option :: tail =>
      println(s"unknown cli option $option"); sys.exit(1)
  }

  val options = cli(Map(), args.toList)
  val zkUrl = options('zkUrl).asInstanceOf[String]
  val zkTimeout = options('zkTimeout).asInstanceOf[String].toInt
  val configuration = new PolynomCyclesConfiguration

  val osHelper = new LinuxHelper()
  val likwidHelper = new LikwidHelper()
  likwidHelper.useDirectMode()
  val cHelper = new CHelper()

  likwidHelper.topologyInit()
  likwidHelper.affinityInit()

  implicit val timer = new JavaTimer(true)
  val zkClient = ZkClient(zkUrl, Duration.fromSeconds(zkTimeout)).withAcl(OPEN_ACL_UNSAFE.asScala)
  val ModelRegex = ".+\\s+(.+)(?:\\s+v?\\d?)\\s+@.+".r
  val cpuModel = likwidHelper.getCpuInfo().osname match {
    case ModelRegex(model) => model.toLowerCase
  }

  val setup = zkClient(s"/$cpuModel").exists() respond {
    case Return(n) =>
      zkClient(s"/$cpuModel").delete(n.stat.getVersion)
    case _ =>
  }

  Await.ready(setup, Duration.fromSeconds(zkTimeout))
  Await.result(zkClient(s"/$cpuModel").create(), Duration.fromSeconds(zkTimeout))

  val topology = likwidHelper.getCpuTopology().threadPool.foldLeft(Map[Int, Seq[Int]]()) {
    (acc, hwThread) =>
      acc + (hwThread.coreId -> acc.getOrElse(hwThread.coreId, Seq()).:+(hwThread.apicId))
  }

  powerapi = Some(PowerMeter.loadModule(HWCCoreSensorModule(None, osHelper, likwidHelper, cHelper)))
  groundTruth = Some(PowerMeter.loadModule(RaplCpuModule(likwidHelper)))

  Sampling("sampling", configuration, topology, powerapi.get, groundTruth.get).run()

  likwidHelper.topologyFinalize()
  likwidHelper.affinityFinalize()

  Processing("sampling", "processing", configuration).run()

  PolynomialCyclesRegression("processing", zkClient(s"/$cpuModel"), zkTimeout, configuration).run()

  zkClient.release()

//  if (samplingOption._1) {
//    val osHelper = new LinuxHelper()
//    val likwidHelper = new LikwidHelper()
//    val cHelper = new CHelper()
//
//    likwidHelper.topologyInit()
//    likwidHelper.affinityInit()
//
//    val topology = likwidHelper.getCpuTopology().threadPool.foldLeft(Map[Int, Seq[Int]]()) {
//      (acc, hwThread) =>
//        acc + (hwThread.coreId -> acc.getOrElse(hwThread.coreId, Seq()).:+(hwThread.apicId))
//    }
//
//    powerapi = Some(PowerMeter.loadModule(HWCCoreSensorModule(None, osHelper, likwidHelper, cHelper)))
//
//    groundTruth = Some({
//      if (raplOption) {
//        PowerMeter.loadModule(RaplCpuModule(likwidHelper))
//      }
//      else if (powerspyOption) {
//        PowerMeter.loadModule(PowerSpyModule(None))
//      }
//      else {
//        PowerMeter.loadModule(G5kOmegaWattModule(None))
//      }
//    })
//
//    Sampling(samplingOption._2, configuration, topology, powerapi.get, groundTruth.get).run()
//
//    likwidHelper.affinityFinalize()
//  }
//
//  if (processingOption._1) {
//    Processing(samplingOption._2, processingOption._2, configuration).run()
//  }
//
//  if (computingOption._1) {
//    PolynomialCyclesRegression(processingOption._2, computingOption._2, configuration).run()
//  }

  shutdownHookThread.start()
  shutdownHookThread.join()
  shutdownHookThread.remove()
  sys.exit(0)
}
