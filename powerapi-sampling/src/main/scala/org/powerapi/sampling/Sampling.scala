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

import akka.actor.{Actor, ActorLogging}
import org.apache.logging.log4j.LogManager
import org.powerapi.PowerMeter
import org.powerapi.configuration.{LibpfmCoreConfiguration, TopologyConfiguration, SamplingConfiguration}
import org.powerapi.core.{OSHelper, Configuration}
import scalax.file.Path

object RENEW

/**
 * Define specific kinds of reporters to be sure that all data are written inside files.
 */
class PowersDisplay(filepath: String) extends org.powerapi.core.APIComponent {
  import java.io.{File, FileOutputStream, PrintWriter}
  import org.powerapi.module.PowerChannel.PowerReport

  var output = new PrintWriter(new FileOutputStream(new File(filepath), true))

  override def postStop(): Unit = {
    output.close()
    super.postStop()
  }

  def receive: Actor.Receive = {
    case msg: PowerReport => report(msg)
    case msg: String => append(msg)
    case RENEW => {
      output.close()
      Path(s"$filepath", '/').delete(true)
      output = new PrintWriter(new FileOutputStream(new File(filepath), true))
    }
  }

  def report(msg: PowerReport): Unit = {
    output.append(s"${msg.power.toWatts}\n")
    output.flush()
  }

  def append(msg: String): Unit = {
    output.append(s"$msg\n")
    output.flush()
  }
}

class CountersDisplay(basepath: String, events: List[String]) extends Actor with ActorLogging  {
  import java.io.{File, FileOutputStream, PrintWriter}
  import org.powerapi.module.libpfm.PerformanceCounterChannel.PCReport
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.Future

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
    case RENEW => {
      outputs.foreach {
        case (event, writer) => {
          writer.close()
          Path(s"$basepath${event.toLowerCase().replace('_', '-').replace(':', '-')}.dat", '/').delete(true)
        }
      }

      outputs = (for(event <- events) yield {
        event -> new PrintWriter(new FileOutputStream(new File(s"$basepath${event.toLowerCase().replace('_', '-').replace(':', '-')}.dat"), true))
      }).toMap
    }
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
class Sampling(samplingDir: String, separator: String, outputPowers: String, baseOutputCounter: String, osHelper: OSHelper, powerapi: PowerMeter, externalPMeter: PowerMeter)
  extends Configuration with SamplingConfiguration with TopologyConfiguration with LibpfmCoreConfiguration {

  import akka.actor.{Actor, ActorSystem, Props}
  import java.util.concurrent.{Executors, ScheduledExecutorService}
  import java.util.concurrent.TimeUnit.MILLISECONDS
  import org.powerapi.core.ConfigValue
  import org.powerapi.core.power._
  import org.powerapi.core.target.All
  import scala.concurrent.duration.DurationInt
  import scala.sys.process.{ProcessLogger, stringSeqToProcess}
  import scalax.file.Path

  private val log = LogManager.getLogger
  private lazy val trash = ProcessLogger(out => {}, err => {})

  lazy val nbSamples: Int = load { _.getInt("powerapi.sampling.nb-samples") } match {
    case ConfigValue(value) => value
    case _ => 1
  }

  lazy val dvfs: Boolean = load { _.getBoolean("powerapi.sampling.dvfs") } match {
    case ConfigValue(value) => value
    case _ => false
  }

  lazy val turbo: Boolean = load { _.getBoolean("powerapi.sampling.turbo") } match {
    case ConfigValue(value) => value
    case _ => false
  }

  lazy val nbMessages: Int = load { _.getInt("powerapi.sampling.nb-messages-per-step") } match {
    case ConfigValue(value) => value
    case _ => 10
  }

  lazy val nbSteps = 100 / 25

  def run(): Unit = {
    Path(s"$samplingDir", '/').deleteRecursively(force = true)
    Path("/tmp/sampling", '/').deleteRecursively(force = true)
    Path("/tmp/sampling", '/').createDirectory()

    var frequencies = osHelper.getCPUFrequencies(topology).toArray.sorted
    var turboFreq: Option[Long] = None

    if(turbo) {
      turboFreq = Some(frequencies.last)
      frequencies = frequencies.slice(0, frequencies.size - 1)
    }

    for(index <- 1 to nbSamples) {
      if(!dvfs) {
        sampling()

        Path(s"$samplingDir/$index/0", '/').createDirectory()
        (Path("/tmp/sampling", '/') * "*.dat").foreach(path => {
          path.moveTo(Path(s"$samplingDir/$index/0/${path.name}", '/'), true)
        })
      }

      else {
        // Intel processor are homogeneous, we cannot control the frequencies per core.
        // Set the default governor with the userspace governor. It allows us to control the frequency.
        Seq("bash", "-c", "echo userspace | tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor > /dev/null").!

        for(frequency <- frequencies) {
          // Set the frequency
          Seq("bash", "-c", s"echo $frequency | tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_setspeed > /dev/null").!

          sampling()

          Path(s"$samplingDir/$index/$frequency", '/').createDirectory()
          (Path("/tmp/sampling", '/') * "*.dat").foreach(path => {
            path.moveTo(Path(s"$samplingDir/$index/$frequency/${path.name}", '/'), true)
          })

          Thread.sleep(5000)
        }

        turboFreq match {
          case Some(frequency) => {
            // Special case for the turbo mode, we can't control the frequency to be able to capture the different heuristics.
            Seq("bash", "-c", s"echo ${frequencies.head} | tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_min_freq > /dev/null").!
            Seq("bash", "-c", s"echo $turboFreq | tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_max_freq > /dev/null").!
            Seq("bash", "-c", "echo ondemand | tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor > /dev/null").!

            sampling()

            Path(s"$samplingDir/$index/$frequency", '/').createDirectory()
            (Path("/tmp/sampling", '/') * "*.dat").foreach(path => {
              path.moveTo(Path(s"$samplingDir/$index/$frequency/${path.name}", '/'), true)
            })
          }
          case _ => {}
        }
      }
    }
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
  private def decreasingLoad(writersSys: ActorSystem, indexes: List[Int], scheduler: ScheduledExecutorService, stressDuration: Int, stepDuration: Int, delimited: Boolean): Unit = {
    val PSFormat = """root\s+([\d]+)\s.*""".r

    val ppids = startStress(indexes, Some(stressDuration))
    val lastWorkerPids = wakeUpStress(ppids)
    var lastCpuLimitPids = List[String]()

    var load = 75
    val loadStep = 25

    scheduler.scheduleAtFixedRate(new Runnable() {
      def run() {
        try {
          lastWorkerPids.foreach(pid => Seq("cpulimit", "-l", load.toString, "-p", pid.toString).run(trash))
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

          if(delimited) {
            writersSys.actorSelection("user/output-cpu") ! separator
            writersSys.actorSelection("user/output-powers") ! separator
          }

          load -= loadStep

          if(load == 0) load = 100
        }
        catch {
          case e: Exception => log.error(e.getMessage); scheduler.shutdown()
        }
      }
    }, stepDuration.seconds.toMillis, stepDuration.seconds.toMillis, MILLISECONDS)
  }

  /**
   * Sampling method.
   */
  private def sampling(): Unit = {
    import org.powerapi.module.libpfm.PerformanceCounterChannel.subscribePCReport

    val firstCore = topology.head
    val remainingCores = topology.tail
    /**
     * Create the HTs combinations (if there is not HT mode, the entire core will be stressed).
     * For example, if the core0 is divided in HT0 and HT1, the combinations will be [0;1;0,1].
     */
    val firstCoreC = (1 to firstCore._2.size).flatMap(firstCore._2.toList.combinations)

    /**
     * Special actor system and writers.
     */
    val writersSys = ActorSystem("writers")
    val externalPMeterDisplay = writersSys.actorOf(Props(classOf[PowersDisplay], s"/tmp/sampling/$outputPowers"), "output-powers")
    val powerapiDisplay = writersSys.actorOf(Props(classOf[CountersDisplay], s"/tmp/sampling/$baseOutputCounter", events), "output-cpu")

    /**
     * Sync.
     */
    var allExPMeter = externalPMeter.monitor(samplingInterval)(All)(MEAN).to(externalPMeterDisplay)
    var allPapi = powerapi.monitor(samplingInterval)(All)(MEAN).to(powerapiDisplay, subscribePCReport)
    Thread.sleep(20.seconds.toMillis)
    allExPMeter.cancel()
    allPapi.cancel()
    writersSys.actorSelection("user/output-cpu") ! RENEW
    writersSys.actorSelection("user/output-powers") ! RENEW

    /**
     * Idle Phase.
     */
    allExPMeter = externalPMeter.monitor(samplingInterval)(All)(MEAN).to(externalPMeterDisplay)
    allPapi = powerapi.monitor(samplingInterval)(All)(MEAN).to(powerapiDisplay, subscribePCReport)
    Thread.sleep(20.seconds.toMillis)
    allExPMeter.cancel()
    allPapi.cancel()
    writersSys.actorSelection("user/output-cpu") ! separator
    writersSys.actorSelection("user/output-powers") ! separator

    /**
     * This loop was built to stress the first core (with/without HTs). Only one loop is executed if there is not a turbo mode, else the other cores are stressed
     * to catch all the steps in the turbo mode. The idea is to get data on the different steps for the first core combinations.
     */
    for(i <- 0 until topology.size if i == 0 || turbo) {
      val indexesToStressTB = remainingCores.slice(0, i).values.flatten.toList

      if(indexesToStressTB.size > 0) {
        val stressPidsCoresTB = startStress(indexesToStressTB, None)
        (Seq("kill", "-SIGCONT") ++ stressPidsCoresTB.map(_.toString)).!(trash)
      }

      for(j <- 0 until firstCoreC.size) {
        var stress = nbMessages
        var step = nbMessages
        var delimited = true

        allExPMeter = externalPMeter.monitor(samplingInterval)(All)(MEAN).to(externalPMeterDisplay)
        allPapi = powerapi.monitor(samplingInterval)(All)(MEAN).to(powerapiDisplay, subscribePCReport)
        val schedulers = scala.collection.mutable.ArrayBuffer[ScheduledExecutorService]()
        val currentCombiFirstC = firstCoreC(j)

        for(k <- 0 until currentCombiFirstC.size) {
          val scheduler = Executors.newScheduledThreadPool(10)
          val indexes = List(currentCombiFirstC(k))
          stress = nbMessages * math.pow(nbSteps, currentCombiFirstC.size).toInt
          decreasingLoad(writersSys, indexes, scheduler, stress, step, delimited)
          step *= nbSteps
          schedulers += scheduler
          delimited = false
        }

        Thread.sleep(stress.seconds.toMillis)
        schedulers.foreach(scheduler => scheduler.shutdown())

        allExPMeter.cancel()
        allPapi.cancel()
      }

      writersSys.stop(externalPMeterDisplay)
      writersSys.stop(powerapiDisplay)
      writersSys.shutdown()

      Seq("bash", "-c", "killall cpulimit stress").!(trash)
    }
  }
}

object Sampling {
  def apply(samplingDir: String, separator: String, outputPowers: String, baseOutputCounter: String, osHelper: OSHelper, powerapi: PowerMeter, externalPMeter: PowerMeter): Sampling = {
    new Sampling(samplingDir, separator, outputPowers, baseOutputCounter, osHelper, powerapi, externalPMeter)
  }
}