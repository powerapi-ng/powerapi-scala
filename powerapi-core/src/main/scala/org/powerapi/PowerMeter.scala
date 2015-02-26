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

import akka.actor.{ ActorRef, ActorRefFactory, ActorSystem, Props }
import akka.event.LoggingReceive
import akka.pattern.{ ask, after, gracefulStop }
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import org.powerapi.PowerMeterMessages.StopAll
import org.powerapi.core.{APIComponent, Monitor}
import org.powerapi.core.MonitorChannel.MonitorStart
import org.powerapi.core._
import org.powerapi.core.target.Target
import org.powerapi.core.power._
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.{ DurationLong, FiniteDuration }

/**
 * PowerAPI object encapsulates all the messages.
 */
object PowerMeterMessages {
  case object StopAll
}

/**
 * Main configuration.
 */
trait PowerMeterConfiguration extends Configuration {
  lazy val timeout: Timeout = load { _.getDuration("powerapi.actors.timeout", TimeUnit.MILLISECONDS) } match {
    case ConfigValue(value) => Timeout(value.milliseconds)
    case _ => Timeout(1l.seconds)
  }
}

/**
 * Represents the main actor of a PowerMeter. Used to handle all the actors created for one PowerMeter.
 */
class PowerMeterActor(eventBus: MessageBus, modules: Seq[PowerModule], timeout: Timeout) extends ActorComponent {
  var clockSupervisor: Option[ActorRef] = None
  var monitorSupervisor: Option[ActorRef] = None
  
  // Starts the mandatory supervisors.
  override def preStart(): Unit = {
    clockSupervisor = Some(context.actorOf(Props(classOf[Clocks], eventBus), "clockSupervisor"))
    monitorSupervisor = Some(context.actorOf(Props(classOf[Monitors], eventBus), "monitorSupervisor"))
    
    modules.foreach(module => {
      module(eventBus)
      module.start(context)
    })
  }
  
  def receive = LoggingReceive {
    case msg: MonitorStart => {
      monitorSupervisor match {
        case Some(actorRef) => sender ! actorRef.ask(msg)(timeout)
        case _ => log.error("The monitor supervisor is not created")
      }
    }
    case StopAll => stopAll()
  } orElse default
  
  def stopAll(): Unit = {
    monitorSupervisor match {
      case Some(actorRef) => context.stop(actorRef)
      case _ => log.error("The monitor supervisor is not created")
    }

    clockSupervisor match {
      case Some(actorRef) => context.stop(actorRef)
      case _ => log.error("The clock supervisor is not created")
    }

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
class PowerMeter(modules: Seq[PowerModule], system: ActorSystem) extends Configuration with PowerMeterConfiguration {
  private val eventBus = new MessageBus
  private val powerMeterActor = system.actorOf(Props(classOf[PowerMeterActor], eventBus, modules, timeout))
  
  /**
   * Triggers a new power monitoring for a specific set of targets at a given frequency.
   *
   * @param frequency Sampling frequency for estimating the power consumption.
   * @param targets System targets to be monitored (and grouped by timestamp).
   * @return the acknowledgment of the triggered power monitoring.
   */
  def monitor(frequency: FiniteDuration)(targets: Target*): PowerMonitoring = {
    val monitor = new Monitor(eventBus, system)
    Await.result(powerMeterActor.ask(MonitorStart("", monitor.muid, frequency, targets.toList))(timeout), timeout.duration)
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
    }, duration + 1l.seconds)

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
  lazy val system = ActorSystem(s"PowerMeter-${System.nanoTime()}")
  
  /**
   * Loads a specific power module as a tuple (sensor,formula).
   *
   * Example: `PowerMeter.load(PowerSpyModule, system)`
   *
   * @param modules: the list of power modules to be loaded within the PowerMeter.
   * @return the resulting instance of the requested power meter.
   */
  def loadModule(modules: PowerModule*): PowerMeter = {
    new PowerMeter(modules, system)
  }
}

/**
 * A PowerModule groups a sets of tightly coupled elements that need to be deployed together.
 *
 * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
 */
trait PowerModule {
  // Underlying classes of a power module, used to create the actors.
  def underlyingSensorsClasses: Seq[(Class[_ <: APIComponent], Seq[Any])]
  def underlyingFormulaeClasses: Seq[(Class[_ <: APIComponent], Seq[Any])]

  protected var eventBus: Option[MessageBus] = None
  private var sensors: List[ActorRef]  = List()
  private var formulae: List[ActorRef] = List()

  def apply(bus: MessageBus): Unit = {
    eventBus = Some(bus)
  }

  /**
   * Initiate a power module
   */
  def start(factory: ActorRefFactory): Unit = {
    eventBus match {
      case Some(bus) => {
        underlyingSensorsClasses.foreach(underlyingSensorClass => {
          sensors :+= factory.actorOf(Props(underlyingSensorClass._1, bus +: underlyingSensorClass._2: _*))
        })
        underlyingFormulaeClasses.foreach(underlyingFormulaClass => {
          formulae :+= factory.actorOf(Props(underlyingFormulaClass._1, bus +: underlyingFormulaClass._2: _*))
        })
      }

      case _ => {}
    }
  }

  /**
   * Stop a power module
   */
  def stop(factory: ActorRefFactory): Unit = {
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
  def to(reference: ActorRef): this.type
  def to(reference: ActorRef, subscribeMethod: MessageBus => ActorRef => Unit): this.type

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
