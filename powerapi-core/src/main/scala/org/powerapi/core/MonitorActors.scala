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
package org.powerapi.core

import java.util.UUID
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.{DurationLong, FiniteDuration}

import akka.actor.SupervisorStrategy.{Directive, Resume}
import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import akka.util.Timeout

import org.powerapi.core.ClockChannel.{startClock, stopClock, subscribeClockTick, unsubscribeClockTick}
import org.powerapi.core.MonitorChannel.{MonitorAggregator, MonitorFrequency, MonitorStart, MonitorStop, MonitorStopAll}
import org.powerapi.core.MonitorChannel.{formatMonitorChildName, publishMonitorTick, setAggregator, setFrequency, stopMonitor, subscribeMonitorsChannel}
import org.powerapi.core.power._
import org.powerapi.core.target.Target
import org.powerapi.core.TickChannel.{subscribeTick, unsubscribeTick}
import org.powerapi.module.FormulaChannel.stopFormula
import org.powerapi.module.PowerChannel.{AggregatePowerReport, RawPowerReport}
import org.powerapi.module.PowerChannel.{render, subscribeAggPowerReport, subscribeRawPowerReport, unsubscribeRawPowerReport, unsubscribeAggPowerReport}
import org.powerapi.module.SensorChannel.stopSensor
import org.powerapi.reporter.ReporterChannel.{startReporter, stopReporter}
import org.powerapi.{PowerDisplay, PowerMonitoring}

/**
  * Main Configuration
  */
trait MonitorConfiguration extends Configuration {
  lazy val timeout: Timeout = load {
    _.getDuration("powerapi.actors.timeout", TimeUnit.MILLISECONDS)
  } match {
    case ConfigValue(value) => Timeout(value.milliseconds)
    case _ => Timeout(15l.seconds)
  }
}

/**
  * One child represents one monitor.
  * Publish ticks to activate the sensor components.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
  */
class MonitorChild(eventBus: MessageBus, muid: UUID, targets: Set[Target]) extends ActorComponent {

  override def preStart(): Unit = {
    subscribeTick(muid)(eventBus)(self)
    super.preStart()
  }

  def receive: Actor.Receive = starting orElse default

  def starting: Actor.Receive = {
    case msg: MonitorStart if msg.muid == muid && msg.targets == targets => start(None)
  }

  /**
    * Running state.
    */
  def running(aggR: AggregatePowerReport, aggregator: Option[Seq[Power] => Power]): Actor.Receive = {
    case tick: Tick => produceMessages(tick)
    case powerReport: RawPowerReport => aggregate(aggR, powerReport, aggregator)
    case msg: MonitorAggregator if msg.muid == muid => setAggregator(aggR, msg.aggregator)
    case msg: MonitorFrequency if msg.muid == muid => unsubscribeTick(msg.muid)(eventBus)(self); setMonitorFrequency(aggR, aggregator, msg.frequency)
    case msg: MonitorStop if msg.muid == muid => stop()
    case _: MonitorStopAll => stop()
  }

  /**
    * Running state when periodic ticks are produced.
    */
  def runningWithClock(aggR: AggregatePowerReport, aggregator: Option[Seq[Power] => Power], currentF: FiniteDuration): Actor.Receive = {
    case msg: MonitorFrequency if msg.muid == muid => unsubscribeClock(currentF); setMonitorFrequency(aggR, aggregator, msg.frequency)
    case msg: MonitorStop if msg.muid == muid => unsubscribeClock(currentF); stop()
    case _: MonitorStopAll => unsubscribeClock(currentF); stop()
  }

  /**
    * Subscribe to the topic for receiving raw report messages.
    */
  def start(aggregator: Option[Seq[Power] => Power]): Unit = {
    subscribeRawPowerReport(muid)(eventBus)(self)
    log.info("monitor is started, muid: {}", muid)
    context.become(running(AggregatePowerReport(muid), aggregator) orElse default)
  }

  /**
    * Start the clock, and attach the monitor to it.
    */
  def subscribeClock(frequency: FiniteDuration): Unit = {
    startClock(frequency)(eventBus)
    subscribeClockTick(frequency)(eventBus)(self)
  }

  /**
    * Stop the clock, and detach the monitor from it.
    */
  def unsubscribeClock(frequency: FiniteDuration): Unit = {
    stopClock(frequency)(eventBus)
    unsubscribeClockTick(frequency)(eventBus)(self)
  }

  /**
    * Change the aggregator associated to the AggregatedPowerReport.
    */
  def setAggregator(aggR: AggregatePowerReport, aggregator: Seq[Power] => Power): Unit = {
    aggR.aggregator = Some(aggregator)
    log.info("aggregator is changed")
    context.become(running(aggR, Some(aggregator)) orElse default)
  }

  /**
    * Change the MonitorChild's frequency.
    */
  def setMonitorFrequency(aggR: AggregatePowerReport, aggregator: Option[Seq[Power] => Power], newF: FiniteDuration): Unit = {
    subscribeClock(newF)
    log.info("frequency is changed")
    context.become(runningWithClock(aggR, aggregator, newF) orElse running(aggR, aggregator) orElse default)
  }

  /**
    * Handle ticks for publishing the targets in the right topic.
    */
  def produceMessages(tick: Tick): Unit = {
    targets.foreach(target => publishMonitorTick(muid, target, tick)(eventBus))
  }

  /**
    * Aggregate all RawPowerReport for the current monitoring.
    */
  def aggregate(aggR: AggregatePowerReport, powerReport: RawPowerReport, aggregator: Option[Seq[Power] => Power]): Unit = {
    if (aggR.size == 0 || aggR.ticks.map(_.timestamp).contains(powerReport.tick.timestamp)) {
      aggR += powerReport
    }
    else {
      render(aggR)(eventBus)
      val newAggr = AggregatePowerReport(muid)
      newAggr.aggregator = aggregator
      newAggr += powerReport
      context.become(running(newAggr, aggregator) orElse default)
    }
  }

  /**
    * Stop to receive raw power reports and kill the monitor actor.
    */
  def stop(): Unit = {
    unsubscribeRawPowerReport(muid)(eventBus)(self)
    log.info("monitor is stopped, muid: {}", muid)
    self ! PoisonPill
  }
}

/**
  * This actor listens the bus on a given topic and reacts on the received messages.
  * It is responsible to handle a pool of child actors which represent all monitors.
  *
  * @author Maxime Colmant <maxime.colmant@gmail.com>
  */
class Monitors(eventBus: MessageBus) extends MonitorConfiguration with Supervisor {

  override def preStart(): Unit = {
    subscribeMonitorsChannel(eventBus)(self)
    super.preStart()
  }

  /**
    * MonitorChild actors can only launch exception if the message received is not handled.
    */
  def handleFailure: PartialFunction[Throwable, Directive] = {
    case _: UnsupportedOperationException => Resume
  }

  def receive: Actor.Receive = running orElse default

  def running: Actor.Receive = {
    case msg: MonitorStart => start(msg)
    case msg: MonitorAggregator => setAggregator(msg)
    case msg: MonitorFrequency => setFrequency(msg)
    case msg: MonitorStop => stop(msg)
    case msg: MonitorStopAll => stopAll(msg)
  }

  /**
    * Start a new monitor.
    *
    * @param msg Message received for starting a monitor.
    */
  def start(msg: MonitorStart): Unit = {
    val name = formatMonitorChildName(msg.muid)
    val child = context.actorOf(Props(classOf[MonitorChild], eventBus, msg.muid, msg.targets), name)
    child ! msg
  }

  /**
    * Change the aggregator of RawPowerReport for a given MonitorChild.
    *
    * @param msg Message received for changing the aggregating function.
    */
  def setAggregator(msg: MonitorAggregator): Unit = {
    val name = formatMonitorChildName(msg.muid)
    context.actorSelection(name) ! msg
  }

  /**
    * Change the frequency for a given MonitorChild.
    *
    * @param msg Message received for changing the frequency.
    */
  def setFrequency(msg: MonitorFrequency): Unit = {
    val name = formatMonitorChildName(msg.muid)
    context.actorSelection(name) ! msg
  }

  /**
    * Stop a given monitor.
    *
    * @param msg Message received for stopping a given monitor.
    */
  def stop(msg: MonitorStop): Unit = {
    val name = formatMonitorChildName(msg.muid)
    context.actorSelection(name) ! msg
  }

  /**
    * Stop all monitor actors.
    *
    * @param msg Message received for stopping all monitor actors.
    */
  def stopAll(msg: MonitorStopAll): Unit = {
    context.actorSelection("*") ! msg
  }
}

/**
  * This class acts like a mirror for interacting with the event bus.
  */
class Monitor(eventBus: MessageBus) extends PowerMonitoring {
  val muid = UUID.randomUUID()

  def apply(aggregator: Seq[Power] => Power): this.type = {
    setAggregator(muid, aggregator)(eventBus)
    this
  }

  def to(output: PowerDisplay): this.type = {
    startReporter(muid, output)(eventBus)
    this
  }

  def unto(output: PowerDisplay): this.type = {
    stopReporter(muid)(eventBus)
    this
  }

  def to(reference: ActorRef): this.type = {
    subscribeAggPowerReport(muid)(eventBus)(reference)
    this
  }

  def unto(reference: ActorRef): this.type = {
    unsubscribeAggPowerReport(muid)(eventBus)(reference)
    this
  }

  def to(reference: ActorRef, subscribeMethod: MessageBus => ActorRef => Unit): this.type = {
    subscribeMethod(eventBus)(reference)
    this
  }

  def unto(reference: ActorRef, unsubscribeMethod: MessageBus => ActorRef => Unit): this.type = {
    unsubscribeMethod(eventBus)(reference)
    this
  }

  def every(frequency: FiniteDuration): this.type = {
    setFrequency(muid, frequency)(eventBus)
    this
  }

  def cancel(): Unit = {
    stopMonitor(muid)(eventBus)
    stopSensor(muid)(eventBus)
    stopFormula(muid)(eventBus)
    stopReporter(muid)(eventBus)
  }
}
