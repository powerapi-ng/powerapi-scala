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

import java.io.{File, FileOutputStream, PrintWriter}

import scala.concurrent.duration.DurationInt
import scala.sys.process.{ProcessLogger, stringSeqToProcess}
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.scalalogging.Logger
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.SuffixFileFilter
import org.joda.time.Period
import org.powerapi.{PowerMeter, PowerMonitoring}
import org.powerapi.core.power._
import org.powerapi.core.target.All
import org.powerapi.module.PowerChannel.AggregatePowerReport
import org.powerapi.module.hwc.HWCChannel.{HWC, HWCReport, subscribeHWCReport, unsubscribeHWCReport}

/**
  * Define specific kinds of reporters to be sure that all data are written inside files.
  */
class PowersDisplay(filepath: String) extends org.powerapi.core.APIComponent {
  var output = new PrintWriter(new FileOutputStream(new File(filepath), true))

  override def postStop(): Unit = {
    output.close()
    super.postStop()
  }

  def receive: Actor.Receive = {
    case msg: AggregatePowerReport => report(msg)
    case msg: String => append(msg)
  }

  def report(msg: AggregatePowerReport): Unit = {
    val power = msg.powerPerDevice.filter(_._1.startsWith("rapl-cpu")).values.map(_.toWatts).sum
    output.append(s"$power\n")
    output.flush()
  }

  def append(msg: String): Unit = {
    output.append(s"$msg\n")
    output.flush()
  }
}

class CountersDisplay(basepath: String, events: Seq[String]) extends Actor with ActorLogging {
  var outputs = (for (event <- events) yield {
    event -> new PrintWriter(new FileOutputStream(new File(s"$basepath${event.toLowerCase().replace('_', '-').replace(':', '-')}.dat"), true))
  }).toMap

  override def postStop(): Unit = {
    outputs.foreach {
      case (_, writer) => writer.close()
    }
    super.postStop()
  }

  def receive: Actor.Receive = {
    case msg: HWCReport => report(msg)
    case msg: String => append(msg)
  }

  def report(msg: HWCReport): Unit = {

    for (event <- events) {
      val counter = msg.values.collect {
        case hwc: HWC if hwc.event == event => hwc.value
      }.sum

      outputs(event).append(s"$counter\n")
      outputs(event).flush()
    }
  }

  def append(msg: String): Unit = {
    outputs.values.foreach(output => {
      output.append(s"$msg\n")
      output.flush()
    })
  }
}

/**
  * Stress all the cpu features in order to compute the underlying formulae.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class Sampling(outputPath: String, configuration: SamplingConfiguration, topology: Map[Int, Seq[Int]], powerapi: PowerMeter, externalPMeter: PowerMeter) {

  private lazy val trash = ProcessLogger(out => {}, err => {})
  private val log = Logger(classOf[Sampling])

  def run(): Unit = {
    val begin = System.currentTimeMillis()

    FileUtils.deleteDirectory(new File(outputPath))
    FileUtils.deleteDirectory(new File("/tmp/sampling"))

    var frequencies = configuration.osHelper.getCPUFrequencies.toArray.sorted
    var turboFreq: Option[Long] = None

    if (configuration.turbo) {
      turboFreq = Some(frequencies.last)
      frequencies = frequencies.slice(0, frequencies.length - 1)
    }

    /**
      * Sync.
      */
    val allExPMeter = externalPMeter.monitor(All)(MEAN).every(configuration.samplingInterval)
    val allPapi = powerapi.monitor(All)(MEAN).every(configuration.samplingInterval)
    Thread.sleep(45.seconds.toMillis)

    for (index <- 1 to configuration.nbSamples) {
      if (!configuration.dvfs) {
        FileUtils.forceMkdir(new File(s"/tmp/sampling/$index/0"))

        sampling(index, 0, turbo = false, allPapi, allExPMeter)

        FileUtils.forceMkdir(new File(s"$outputPath/$index/0"))
        new File(s"/tmp/sampling/$index/0").list(new SuffixFileFilter(".dat")).foreach(name => {
          FileUtils.moveFile(new File(s"/tmp/sampling/$index/0/$name"), new File(s"$outputPath/$index/0/$name"))
        })
      }

      /*else {
        // Intel processor are homogeneous, we cannot control the frequencies per core.
        // Set the default governor with the userspace governor. It allows us to control the frequency.
        configuration.topology.values.flatten.foreach {
          case cpu => (Seq("echo", "userspace") #>> new File(s"/sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_governor")).!
        }

        for (frequency <- frequencies) {
          FileUtils.forceMkdir(new File(s"/tmp/sampling/$index/$frequency"))

          // Set the frequency
          configuration.topology.values.flatten.foreach {
            case cpu => (Seq("echo", s"$frequency") #>> new File(s"/sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_setspeed")).!
          }

          sampling(index, frequency, turbo = false, allPapi, allExPMeter)

          FileUtils.forceMkdir(new File(s"$outputPath/$index/$frequency"))
          new File(s"/tmp/sampling/$index/$frequency").list(new SuffixFileFilter(".dat")).foreach(name => {
            FileUtils.moveFile(new File(s"/tmp/sampling/$index/$frequency/$name"), new File(s"$outputPath/$index/$frequency/$name"))
          })
        }

        turboFreq match {
          case Some(frequency) =>
            FileUtils.forceMkdir(new File(s"/tmp/sampling/$index/$frequency"))

            // Special case for the turbo mode, we can't control the frequency to be able to capture the different heuristics.
            configuration.topology.values.flatten.foreach {
              case cpu =>
                (Seq("echo", s"${frequencies.head}") #>> new File(s"/sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_min_freq")).!
                (Seq("echo", s"$frequency") #>> new File(s"/sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_max_freq")).!
                (Seq("echo", "ondemand") #>> new File(s"/sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_governor")).!
            }

            sampling(index, frequency, turbo = true, allPapi, allExPMeter)

            FileUtils.forceMkdir(new File(s"$outputPath/$index/$frequency"))
            new File(s"/tmp/sampling/$index/$frequency").list(new SuffixFileFilter(".dat")).foreach(name => {
              FileUtils.moveFile(new File(s"/tmp/sampling/$index/$frequency/$name"), new File(s"$outputPath/$index/$frequency/$name"))
            })
          case _ =>
        }
      }*/
    }

    allPapi.cancel()
    allExPMeter.cancel()
    val end = System.currentTimeMillis()
    log.info(s"Sampling duration: {}", configuration.formatter.print(new Period(end - begin)))
  }

  /**
    * Sampling method, stress the processor in order to get data
    */
  private def sampling(index: Int, frequency: Long, turbo: Boolean, allPapi: PowerMonitoring, allExPMeter: PowerMonitoring): Unit = {
    val firstCore = topology.head
    val remainingCores = topology.tail

    /**
      * Special actor system and writers.
      */
    val writersSys = ActorSystem("writers")

    val externalPMeterDisplay = writersSys.actorOf(Props(classOf[PowersDisplay], s"/tmp/sampling/$index/$frequency/${configuration.outputPowers}"), "output-powers")
    val powerapiDisplay = writersSys.actorOf(Props(classOf[CountersDisplay], s"/tmp/sampling/$index/$frequency/${configuration.baseOutput}", configuration.events), "output-cpu")

    /**
      * Idle Phase.
      */
    allExPMeter.to(externalPMeterDisplay)
    allPapi.to(powerapiDisplay, subscribeHWCReport(allPapi.muid, All))

    Thread.sleep(30.seconds.toMillis)

    allExPMeter.unto(externalPMeterDisplay)
    allPapi.unto(powerapiDisplay, unsubscribeHWCReport(allPapi.muid, All))
    writersSys.actorSelection("user/output-cpu") ! configuration.separator
    writersSys.actorSelection("user/output-powers") ! configuration.separator

    /**
      * This loop was built to stress the first core (with/without HTs).
      */
    for (i <- 0 until topology.size if i == 0 || turbo) {
      var previous = List[Int]()
      val indexesToStressTB = remainingCores.slice(0, i).values.flatten.toList

      // Core to stress for enabling the Turbo mode.
      if (indexesToStressTB.nonEmpty) {
        (Seq("kill", "-SIGCONT") ++ startStress(indexesToStressTB, None).map(_.toString)).!(trash)
      }

      // Workload on the first core.
      for (index <- firstCore._2) {
        if (previous.nonEmpty) {
          (Seq("kill", "-SIGCONT") ++ startStress(previous, None).map(_.toString)).!(trash)
        }

        allExPMeter.to(externalPMeterDisplay)
        allPapi.to(powerapiDisplay, subscribeHWCReport(allPapi.muid, All))

        decreasingLoad(writersSys, index)

        allExPMeter.unto(externalPMeterDisplay)
        allPapi.unto(powerapiDisplay, unsubscribeHWCReport(allPapi.muid, All))

        Thread.sleep(2.seconds.toMillis)

        previous :+= index
      }

      Seq("killall", "cpulimit", "stress").!(trash)
    }

    allExPMeter.unto(externalPMeterDisplay)
    allPapi.unto(powerapiDisplay, unsubscribeHWCReport(allPapi.muid, All))
    writersSys.stop(externalPMeterDisplay)
    writersSys.stop(powerapiDisplay)
    writersSys.terminate()
  }

  /**
    * Decreasing load with the stress command.
    */
  private def decreasingLoad(writersSys: ActorSystem, index: Int): Unit = {
    val PSFormat = """root\s+([\d]+)\s.*""".r

    val ppids = startStress(List(index), Some(configuration.steps.size * configuration.stepDuration))
    val lastWorkerPids = wakeUpStress(ppids)
    var lastCpuLimitPids = List[String]()

    for (step <- configuration.steps) {
      lastWorkerPids.foreach(pid => Seq("cpulimit", "-l", s"$step", "-p", s"$pid").run(trash))
      lastCpuLimitPids.foreach(pid => Seq("kill", "-9", pid).!(trash))

      val cmd = Seq("ps", "-ef") #> Seq("egrep", "-i", s"'${lastWorkerPids.map("cpulimit.*-p " + _.toString + ".*").mkString("|")}'") #> Seq("grep", "-v", "egrep")
      var outputs = List[String]()
      var nbMs = 0

      while (outputs.size < lastWorkerPids.size && nbMs < 500) {
        outputs = cmd.lineStream_!.toList
        Thread.sleep(100.milliseconds.toMillis)
        nbMs += 100
      }

      lastCpuLimitPids = (for (line <- outputs) yield {
        line match {
          case PSFormat(pid) => pid.toInt
          case _ => -1
        }
      }).map(_.toString)

      Thread.sleep(configuration.stepDuration.seconds.toMillis)

      writersSys.actorSelection("user/output-cpu") ! configuration.separator
      writersSys.actorSelection("user/output-powers") ! configuration.separator
    }
  }

  /**
    * Start a stress pinned to one HT/Core.
    */
  private def startStress(indexes: List[Int], duration: Option[Int]): List[Int] = {
    val ppids = scala.collection.mutable.ListBuffer[Int]()

    indexes.foreach(index => {
      val ppid = duration match {
        case Some(t) => Seq("bash", "scripts/start.bash", s"stress -c 1 -t $t").lineStream.head.trim.toInt
        case None => Seq("bash", "scripts/start.bash", s"stress -c 1").lineStream.head.trim.toInt
      }

      ppids += ppid
      Seq("taskset", "-cp", s"$index", s"$ppid").!(trash)
    })

    ppids.toList
  }

  /**
    * Wake up stresses.
    */
  private def wakeUpStress(ppids: List[Int]): List[Int] = {
    var outputs = List[String]()
    // We assume the sampling is isolated.
    val existingStress = Seq("pgrep", "stress").lineStream_!.toArray

    (Seq("kill", "-SIGCONT") ++ ppids.map(_.toString)).!(trash)

    // Check if all the stress are launched.
    // Only stress -c 1 are used during the sampling.
    while (outputs.size < (existingStress.length + (2 * ppids.size))) {
      outputs = Seq("pgrep", "stress").lineStream_!.toList
    }

    outputs.map(_.trim.toInt).diff(existingStress.map(_.trim.toInt)).diff(ppids)
  }
}

object Sampling {

  def apply(outputPath: String, configuration: SamplingConfiguration, topology: Map[Int, Seq[Int]], powerapi: PowerMeter, groundTruth: PowerMeter): Sampling = {
    new Sampling(outputPath, configuration, topology, powerapi, groundTruth)
  }
}
