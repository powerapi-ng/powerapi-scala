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
package org.powerapi

import java.util.UUID
import java.util.concurrent.TimeUnit

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.concurrent.{Await, Future}
import akka.actor._
import akka.pattern.{after, ask, gracefulStop, pipe}
import akka.util.Timeout
import org.powerapi.core.MonitorChannel.{startMonitor, stopAllMonitor}
import org.powerapi.core.power._
import org.powerapi.core.target.Target
import org.powerapi.core.{ActorComponent, Clocks, ConfigValue, Configuration, MessageBus, Monitor, Monitors}
import org.powerapi.module.FormulaChannel.{startFormula, stopAllFormula}
import org.powerapi.module.PowerChannel.AggregatePowerReport
import org.powerapi.module.SensorChannel.{startSensor, stopAllSensor}
import org.powerapi.module.{Formula, Formulas, Sensor, Sensors}
import org.powerapi.reporter.ReporterChannel.stopAllReporter
import org.powerapi.reporter.Reporters


case class MonitorMessage(targets: Set[Target])

case class WaitForMessage(duration: FiniteDuration)

object ShutdownMessage

/**
  * Main configuration.
  */
class PowerMeterConfiguration extends Configuration(None) {
  lazy val timeout: Timeout = load {
    _.getDuration("powerapi.actors.timeout", TimeUnit.MILLISECONDS)
  } match {
    case ConfigValue(value) => Timeout(value.milliseconds)
    case _ => Timeout(15l.seconds)
  }
}

/**
  * Main actor of a PowerMeter object.
  */
class PowerMeterActor(eventBus: MessageBus, modules: Seq[PowerModule]) extends ActorComponent {
  val clocks = context.actorOf(Props(classOf[Clocks], eventBus), "clocks")
  val monitors = context.actorOf(Props(classOf[Monitors], eventBus), "monitors")
  val sensors = context.actorOf(Props(classOf[Sensors], eventBus), "sensors")
  val formulas = context.actorOf(Props(classOf[Formulas], eventBus), "formulas")
  val reporters = context.actorOf(Props(classOf[Reporters], eventBus), "reporters")

  def receive: Actor.Receive = running orElse default

  def running: Actor.Receive = {
    case msg: MonitorMessage => monitor(msg)
    case msg: WaitForMessage => waitFor(msg)
    case ShutdownMessage => shutdown()
  }

  def monitor(msg: MonitorMessage): Unit = {
    val monitorO = new Monitor(eventBus)
    startMonitor(monitorO.muid, msg.targets)(eventBus)

    val modCombinations = for {
      module: PowerModule <- modules
      target: Target <- msg.targets
    } yield (module, target)

    modCombinations.par foreach {
      case (module, target) =>
        module.sensor match {
          case Some((claz, args)) => startSensor(monitorO.muid, target, claz, Seq(eventBus, monitorO.muid, target) ++ args)(eventBus)
          case _ =>
        }
        module.formula match {
          case Some((claz, args)) => startFormula(monitorO.muid, target, claz, Seq(eventBus, monitorO.muid, target) ++ args)(eventBus)
          case _ =>
        }
    }

    sender ! monitorO
  }

  def waitFor(msg: WaitForMessage): Unit = {
    after(msg.duration, using = context.system.scheduler) {
      Future successful "waitFor completed"
    } pipeTo sender
  }

  def shutdown(): Unit = {
    stopAllMonitor(eventBus)
    stopAllSensor(eventBus)
    stopAllFormula(eventBus)
    stopAllReporter(eventBus)

    clocks ! PoisonPill
    monitors ! PoisonPill
    sensors ! PoisonPill
    formulas ! PoisonPill
    reporters ! PoisonPill
    self ! PoisonPill
  }
}

/**
  * Implements the main features for configuring a <i>Software-Defined Power Meter</i>.
  *
  * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
  * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
  */
class PowerMeter(factory: ActorRefFactory, modules: Seq[PowerModule]) extends PowerMeterConfiguration {
  private val eventBus = new MessageBus
  private val underlyingActor = factory.actorOf(Props(classOf[PowerMeterActor], eventBus, modules))

  /**
    * Trigger a new power monitoring for a specific set of targets at a given frequency.
    *
    * @param targets System targets to be monitored (and grouped by timestamp).
    * @return the acknowledgment of the triggered power monitoring.
    */
  def monitor(targets: Target*): PowerMonitoring = {
    Await.result(underlyingActor.ask(MonitorMessage(targets.toSet))(timeout), timeout.duration).asInstanceOf[Monitor]
  }

  /**
    * Block and actively wait for a specific duration before returning.
    *
    * @param duration duration to wait for.
    * @return the instance of the underlying software power meter.
    */
  def waitFor(duration: FiniteDuration): this.type = {
    Await.result(underlyingActor.ask(WaitForMessage(duration))(duration + 1L.seconds), duration + 1L.seconds)
    this
  }

  /**
    * Shutdown the current instance of power meter.
    */
  def shutdown(): Unit = {
    underlyingActor ! ShutdownMessage
    Await.result(gracefulStop(underlyingActor, timeout.duration), timeout.duration)
  }
}

object PowerMeter {
  lazy val system = ActorSystem(s"PowerMeter-${System.nanoTime}")

  /**
    * Loads a specific power module as a tuple (sensor,formula).
    *
    * Example: `PowerMeter.loadModule(PowerSpyModule)`
    *
    * @param modules PowerModule to be loaded within the PowerMeter.
    * @return the resulting instance of the requested power meter.
    */
  def loadModule(modules: PowerModule*): PowerMeter = {
    new PowerMeter(system, modules)
  }
}

/**
  * A PowerModule groups a set of tightly coupled API components that need to be deployed together.
  *
  * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
  */
trait PowerModule {

  def sensor: Option[(Class[_ <: Sensor], Seq[Any])]

  def formula: Option[(Class[_ <: Formula], Seq[Any])]
}

/**
  * Defines the interface that can be used to control a power monitoring.
  *
  * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
  */
trait PowerMonitoring {
  /**
    * Unique ID
    */
  def muid: UUID

  /**
    * Change the aggregation function to apply on raw power reports.
    */
  def apply(aggregator: Seq[Power] => Power): this.type

  /**
    * Change frequency when periodic ticks are internally created by a clock.
    */
  def every(frequency: FiniteDuration): this.type

  /**
    * Configure the power display to use for rendering power estimation.
    */
  def to(output: PowerDisplay): this.type

  def to(reference: ActorRef): this.type

  def to(reference: ActorRef, subscribeMethod: MessageBus => ActorRef => Unit): this.type

  /**
    * Remove the power display used for rendering power estimation.
    */
  def unto(output: PowerDisplay): this.type

  def unto(reference: ActorRef): this.type

  def unto(reference: ActorRef, unsubscribeMethod: MessageBus => ActorRef => Unit): this.type

  /**
    * Cancel the subscription and stop the associated monitoring.
    */
  def cancel()
}

/**
  * Defines the interface used by the power meter to configure the power display.
  *
  * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
  */
trait PowerDisplay {

  def display(aggregatePowerReport: AggregatePowerReport)
}
