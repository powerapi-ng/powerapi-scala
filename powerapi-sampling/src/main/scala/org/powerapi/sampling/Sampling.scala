/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2015 Inria, University of Lille 1.
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

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import java.io.{File, FileOutputStream, PrintWriter}
import org.apache.logging.log4j.LogManager
import org.joda.time.Period
import org.powerapi.PowerMeter
import org.powerapi.module.PowerChannel.AggregatePowerReport
import org.powerapi.module.libpfm.{LibpfmHelper, LibpfmCoreSensorModule}
import org.powerapi.module.libpfm.PerformanceCounterChannel.{subscribePCReport, PCReport}
import org.powerapi.core.power._
import org.powerapi.core.target.All
import org.powerapi.module.powerspy.PowerSpyModule
import scala.concurrent.duration.DurationInt
import scala.sys.process.{ProcessLogger, stringSeqToProcess}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalax.file.Path

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
    output.append(s"${msg.power.toWatts}\n")
    output.flush()
  }

  def append(msg: String): Unit = {
    output.append(s"$msg\n")
    output.flush()
  }
}

class CountersDisplay(basepath: String, events: Set[String]) extends Actor with ActorLogging  {
  var outputs = (for(event <- events) yield {
    event -> new PrintWriter(new FileOutputStream(new File(s"$basepath${event.toLowerCase().replace('_', '-').replace(':', '-')}.dat"), true))
  }).toMap

  override def postStop(): Unit = {
    outputs.foreach {
      case (_, writer) => writer.close()
    }
    super.postStop()
  }

  def receive: Actor.Receive = {
    case msg: PCReport => report(msg)
    case msg: String => append(msg)
  }

  def report(msg: PCReport): Unit = {
    for((event, wrappers) <- msg.wrappers.groupBy(_.event)) {
      val future = Future.sequence(wrappers.foldLeft(List[Future[Long]]())((acc, elt) => acc ++ elt.values))

      future onSuccess {
        case values: List[Long] => {
          val counter = values.foldLeft(0l)((acc, value) => acc + value)
          outputs(event).append(s"$counter\n")
          outputs(event).flush()
        }
      }

      future onFailure {
        case ex: Throwable => {
          log.warning("An error occurred: {}", ex.getMessage)
        }
      }
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
class Sampling(outputPath: String, configuration: SamplingConfiguration, libpfmHelper: LibpfmHelper, powerapi: PowerMeter, externalPMeter: PowerMeter) {

  private val log = LogManager.getLogger
  private lazy val trash = ProcessLogger(out => {}, err => {})

  def run(): Unit = {
    val begin = System.currentTimeMillis()

    Path(outputPath, '/').deleteRecursively(force = true)
    Path("/tmp/sampling", '/').deleteRecursively(force = true)

    var frequencies = configuration.osHelper.getCPUFrequencies.toArray.sorted
    var turboFreq: Option[Long] = None

    if(configuration.turbo) {
      turboFreq = Some(frequencies.last)
      frequencies = frequencies.slice(0, frequencies.size - 1)
    }

    for(index <- 1 to configuration.nbSamples) {
      if(!configuration.dvfs) {
        Path(s"/tmp/sampling/$index/0", '/').createDirectory()

        sampling(index, 0, false)

        Path(s"$outputPath/$index/0", '/').createDirectory()
        (Path(s"/tmp/sampling/$index/0", '/') * "*.dat").foreach(path => {
          path.moveTo(Path(s"$outputPath/$index/0/${path.name}", '/'), true)
        })
      }

      else {
        // Intel processor are homogeneous, we cannot control the frequencies per core.
        // Set the default governor with the userspace governor. It allows us to control the frequency.
        Seq("bash", "-c", "echo userspace | tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor > /dev/null").!

        for(frequency <- frequencies) {
          Path(s"/tmp/sampling/$index/$frequency", '/').createDirectory()

          // Set the frequency
          Seq("bash", "-c", s"echo $frequency | tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_setspeed > /dev/null").!

          sampling(index, frequency, false)

          Path(s"$outputPath/$index/$frequency", '/').createDirectory()
          (Path(s"/tmp/sampling/$index/$frequency", '/') * "*.dat").foreach(path => {
            path.moveTo(Path(s"$outputPath/$index/$frequency/${path.name}", '/'), true)
          })
        }

        turboFreq match {
          case Some(frequency) => {
            Path(s"/tmp/sampling/$index/$frequency", '/').createDirectory()

            // Special case for the turbo mode, we can't control the frequency to be able to capture the different heuristics.
            Seq("bash", "-c", s"echo ${frequencies.head} | tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_min_freq > /dev/null").!
            Seq("bash", "-c", s"echo $frequency | tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_max_freq > /dev/null").!
            Seq("bash", "-c", "echo ondemand | tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor > /dev/null").!

            sampling(index, frequency, true)

            Path(s"$outputPath/$index/$frequency", '/').createDirectory()
            (Path(s"/tmp/sampling/$index/$frequency", '/') * "*.dat").foreach(path => {
              path.moveTo(Path(s"$outputPath/$index/$frequency/${path.name}", '/'), true)
            })
          }
          case _ => {}
        }
      }
    }

    libpfmHelper.deinit()
    val end = System.currentTimeMillis()
    log.info(s"Sampling duration: {}", configuration.formatter.print(new Period(end - begin)))
  }

  /**
   * Start a stress pinned to one HT/Core.
   */
  private def startStress(indexes: List[Int], duration: Option[Int]): List[Int] = {
    val ppids = scala.collection.mutable.ListBuffer[Int]()

    indexes.foreach(index => {
      val ppid = duration match {
        case Some(t) => Seq("bash", "scripts/start.bash", s"stress -c 1 -t $t").lineStream(0).trim.toInt
        case None => Seq("bash", "scripts/start.bash", s"stress -c 1").lineStream(0).trim.toInt
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
    while(outputs.size < (existingStress.size + (2 * ppids.size))) {
      outputs = Seq("pgrep", "stress").lineStream_!.toList
    }

    outputs.map(_.trim.toInt).diff(existingStress.map(_.trim.toInt)).diff(ppids)
  }

  /**
   * Decreasing load with the stress command.
   */
  private def decreasingLoad(writersSys: ActorSystem, index: Int): Unit = {
    val PSFormat = """root\s+([\d]+)\s.*""".r

    val ppids = startStress(List(index), Some(configuration.steps.size * configuration.stepDuration))
    val lastWorkerPids = wakeUpStress(ppids)
    var lastCpuLimitPids = List[String]()

    for(step <- configuration.steps) {
      lastWorkerPids.foreach(pid => Seq("cpulimit", "-l", s"$step", "-p", s"$pid").run(trash))
      lastCpuLimitPids.foreach(pid => Seq("kill", "-9", pid).!(trash))

      val cmd = Seq("bash", "-c", "ps -ef") #> Seq("bash", "-c", "egrep -i '" + lastWorkerPids.map("cpulimit.*-p " + _.toString + ".*").mkString("|") + "'") #> Seq("bash", "-c", "grep -v egrep")
      var outputs = List[String]()
      var nbMs = 0

      while(outputs.size < lastWorkerPids.size && nbMs < 500) {
        outputs = cmd.lineStream_!.toList
        Thread.sleep(100.milliseconds.toMillis)
        nbMs += 100
      }

      lastCpuLimitPids = (for(line <- outputs) yield {
        line match {
          case PSFormat(pid) => pid.toInt
          case _ => -1
        }
      }).toList.map(_.toString)

      Thread.sleep(configuration.stepDuration.seconds.toMillis)

      writersSys.actorSelection("user/output-cpu") ! configuration.separator
      writersSys.actorSelection("user/output-powers") ! configuration.separator
    }
  }

  /**
   * Sampling method, stress the processor in order to get data
   */
  private def sampling(index: Int, frequency: Long, turbo: Boolean): Unit = {
    val firstCore = configuration.topology.head
    val remainingCores = configuration.topology.tail

    /**
     * Special actor system and writers.
     */
    val writersSys = ActorSystem("writers")

    /**
     * Sync.
     */
    var allExPMeter = externalPMeter.monitor(configuration.samplingInterval)(All)(MEAN)
    var allPapi = powerapi.monitor(configuration.samplingInterval)(All)(MEAN)
    Thread.sleep(15.seconds.toMillis)
    allExPMeter.cancel()
    allPapi.cancel()

    Thread.sleep(2.seconds.toMillis)

    val externalPMeterDisplay = writersSys.actorOf(Props(classOf[PowersDisplay], s"/tmp/sampling/$index/$frequency/${configuration.outputPowers}"), "output-powers")
    val powerapiDisplay = writersSys.actorOf(Props(classOf[CountersDisplay], s"/tmp/sampling/$index/$frequency/${configuration.baseOutput}", configuration.events), "output-cpu")

    /**
     * Idle Phase.
     */
    allExPMeter = externalPMeter.monitor(configuration.samplingInterval)(All)(MEAN).to(externalPMeterDisplay)
    allPapi = powerapi.monitor(configuration.samplingInterval)(All)(MEAN).to(powerapiDisplay, subscribePCReport)
    Thread.sleep(configuration.stepDuration.seconds.toMillis)
    allExPMeter.cancel()
    allPapi.cancel()
    writersSys.actorSelection("user/output-cpu") ! configuration.separator
    writersSys.actorSelection("user/output-powers") ! configuration.separator

    Thread.sleep(2.seconds.toMillis)

    /**
     * This loop was built to stress the first core (with/without HTs).
     */
    for(i <- 0 until configuration.topology.size if i == 0 || turbo) {
      var previous = List[Int]()
      val indexesToStressTB = remainingCores.slice(0, i).values.flatten.toList

      // Core to stress for enabling the Turbo mode.
      if(indexesToStressTB.size > 0) {
        (Seq("kill", "-SIGCONT") ++ startStress(indexesToStressTB, None).map(_.toString)).!(trash)
      }

      // Workload on the first core.
      for(index <- firstCore._2) {
        if(previous.size > 0) {
          (Seq("kill", "-SIGCONT") ++ startStress(previous, None).map(_.toString)).!(trash)
        }

        allExPMeter = externalPMeter.monitor(configuration.samplingInterval)(All)(MEAN).to(externalPMeterDisplay)
        allPapi = powerapi.monitor(configuration.samplingInterval)(All)(MEAN).to(powerapiDisplay, subscribePCReport)

        decreasingLoad(writersSys, index)

        allExPMeter.cancel()
        allPapi.cancel()

        Thread.sleep(2.seconds.toMillis)

        previous :+= index
      }

      Seq("bash", "-c", "killall cpulimit stress").!(trash)
    }

    allExPMeter.cancel()
    allPapi.cancel()
    writersSys.stop(externalPMeterDisplay)
    writersSys.stop(powerapiDisplay)
    writersSys.shutdown()
  }
}

object Sampling {
  @volatile var powerapi: Option[PowerMeter] = None
  @volatile var externalPMeter: Option[PowerMeter] = None

  def apply(outputPath: String, configuration: SamplingConfiguration, libpfmHelper: LibpfmHelper): Sampling = {
    libpfmHelper.init()
    powerapi = Some(PowerMeter.loadModule(LibpfmCoreSensorModule(None, libpfmHelper, configuration.events)))
    externalPMeter = Some(PowerMeter.loadModule(PowerSpyModule()))
    new Sampling(outputPath, configuration, libpfmHelper, powerapi.get, externalPMeter.get)
  }
}
