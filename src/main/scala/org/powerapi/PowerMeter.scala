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
package org.powerapi

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.{ FiniteDuration, DurationInt }

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorRefFactory, ActorSystem, PoisonPill, Props }
import akka.event.LoggingReceive
import akka.pattern.{ after, ask, gracefulStop }
import akka.util.Timeout

import org.powerapi.core.{ ActorComponent, MessageBus }
import org.powerapi.core.target.Target
import org.powerapi.core.power._

/**
 * PowerAPI object encapsulates all the messages.
 */
object PowerMeterMessages {
  case object StopAll
}

/**
* Object used to share the timeout.
*/
object DefaultTimeout {
  val timeout = Timeout(1.seconds)
}

/**
 * Represents the main actor of a PowerMeter. Used to handle all the actors created for one PowerMeter.
 */
class PowerMeterActor(modules: Seq[PowerModule], eventBus: MessageBus) extends ActorComponent {
  import PowerMeterMessages._
  import org.powerapi.core.{ Clocks, Monitors }
  import org.powerapi.core.ClockChannel.stopAllClock
  import org.powerapi.core.MonitorChannel.{ MonitorStart, stopAllMonitor }
  
  implicit val timeout = DefaultTimeout.timeout
  
  var clockSupervisor: ActorRef   = null
  var monitorSupervisor: ActorRef = null
  
  // Starts the mandatory supervisors.
  override def preStart(): Unit = {
    clockSupervisor   = context.actorOf(Props(classOf[Clocks], eventBus), "clockSupervisor")
    monitorSupervisor = context.actorOf(Props(classOf[Monitors], eventBus), "monitorSupervisor")
    
    modules.foreach(module => {
      module.start(context, eventBus)
    })
  }
  
  def receive = LoggingReceive {
    case msg: MonitorStart => monitorSupervisor ! msg
    case StopAll => stopAll
  } orElse default
  
  def stopAll {
    context.stop(monitorSupervisor)
    context.stop(clockSupervisor)
    modules.foreach(module => {
      module.stop(context)
    })
  }
}

/**
 * Implements the main functionalities for configuring a <i>Software-Defined Power Meter</i>.
 *
 * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
 * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
 */
class PowerMeter(modules: Seq[PowerModule], system: ActorSystem) {
  import org.powerapi.core.Monitor
  import org.powerapi.core.MonitorChannel.MonitorStart
  import PowerMeterMessages._
  
  implicit val timeout = DefaultTimeout.timeout

  private val eventBus = new MessageBus
  private val powerMeterActor = system.actorOf(Props(classOf[PowerMeterActor], modules, eventBus))
  
  /**
   * Triggers a new power monitoring for a specific set of targets at a given frequency.
   *
   * @param frequency Sampling frequency for estimating the power consumption.
   * @param targets System targets to be monitored (and grouped by timestamp).
   * @return the acknowledgment of the triggered power monitoring.
   */
  def monitor(frequency: FiniteDuration)(targets: Target*): PowerMonitoring = {
    val monitor = new Monitor(eventBus, system)
    powerMeterActor ! MonitorStart("", monitor.muid, frequency, targets.toList)
    monitor
  }

  /**
   * Blocks and actively waits for a specific duration before returning.
   *
   * @param duration: duration to wait for.
   * @return the instance of the power meter.
   */
  def waitFor(duration: FiniteDuration): this.type = {
    import scala.concurrent.ExecutionContext.Implicits._
    Await.result(after(duration, using = system.scheduler) {
          Future successful "waitFor completed"
        }, duration + 1.seconds)
    this
  }

  /**
   * Shuts down the current instance of power meter.
   */
  def shutdown(): Unit = {
    powerMeterActor ! StopAll
    Await.result(gracefulStop(powerMeterActor, timeout.duration), timeout.duration)
  }
}

object PowerMeter {
  implicit lazy val system = ActorSystem(s"PowerMeter-${System.currentTimeMillis}")
  
  /**
   * Loads a specific power module as a tuple (sensor,formula).
   *
   * Example: `PowerMeter.load(PowerSpyModule, system)`
   *
   * @param modules: the list of power modules to be loaded within the PowerMeter.
   * @return the resulting instance of the requested power meter.
   */
  def load(modules: PowerModule*): PowerMeter = {
    new PowerMeter(modules, system)
  }
}


/**
 * A PowerModule groups a sets of tightly coupled elements that need to be deployed together.
 *
 * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
 */
trait PowerModule {
  import org.powerapi.core.APIComponent

  implicit val timeout = DefaultTimeout.timeout

  // Underlying classes of a power module, used to create the actors.
  def underlyingSensorsClass: Seq[(Class[_ <: APIComponent], Seq[Any])]
  def underlyingFormulaeClass: Seq[(Class[_ <: APIComponent], Seq[Any])]
  
  protected var sensors: List[ActorRef]  = List()
  protected var formulae: List[ActorRef] = List()

  /**
   * Initiate a power module
   */
  def start(factory: ActorRefFactory, eventBus: MessageBus) {
    underlyingSensorsClass.foreach(underlyingSensorClass => {
      sensors :+= factory.actorOf(Props(underlyingSensorClass._1, eventBus +: underlyingSensorClass._2:_*))
    })
    underlyingFormulaeClass.foreach(underlyingFormulaClass => {
      formulae :+= factory.actorOf(Props(underlyingFormulaClass._1, eventBus +: underlyingFormulaClass._2:_*))
    })
  }

  /**
   * Stop a power module
   */
  def stop(factory: ActorRefFactory) {
    sensors.foreach(sensor => factory.stop(sensor))
    formulae.foreach(formula => factory.stop(formula))
  }
}

/**
 * Defines the interface that can be used to control a power monitoring.
 *
 * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
 */
trait PowerMonitoring {
  /**
   * Configures the aggregation function to apply on power estimation per sample.
   */
  def apply(aggregator: Seq[Power] => Power): this.type

  /**
   * Configures the power display to use for rendering power estimations.
   */
  def to(output: PowerDisplay): this.type

  /**
   * Cancel the subscription and stops the associated monitoring.
   */
  def cancel()
}

/**
 * Defines the interface used by the power meter to configure the power display.
 *
 * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
 */
trait PowerDisplay {

  /**
   * Displays data from power reports.
   */
  def display(timestamp: Long, target: Target, device: String, power: Power)
}

