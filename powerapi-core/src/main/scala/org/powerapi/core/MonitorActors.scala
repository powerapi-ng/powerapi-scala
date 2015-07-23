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
package org.powerapi.core

import akka.util.Timeout
import akka.actor.SupervisorStrategy.{Directive, Resume}
import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.event.LoggingReceive
import akka.pattern.ask
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.powerapi.core.ClockChannel.ClockTick
import org.powerapi.core.power._
import org.powerapi.core.target.Target
import org.powerapi.{PowerDisplay, PowerMonitoring}
import org.powerapi.core.ClockChannel.{startClock, stopClock, subscribeClockTick, unsubscribeClockTick}
import org.powerapi.core.MonitorChannel.{GetMonitoredProcesses, MonitorAggFunction, MonitorStart, MonitorStarted, MonitorStop, MonitorStopAll, formatMonitorChildName, subscribeMonitorsChannel}
import org.powerapi.core.MonitorChannel.{publishMonitorTick, setAggFunction, stopMonitor}
import org.powerapi.module.PowerChannel.{AggregatePowerReport, RawPowerReport, render, subscribeRawPowerReport, unsubscribeRawPowerReport, subscribeAggPowerReport}
import org.powerapi.module.SensorChannel.{monitorAllStopped, monitorStopped}
import org.powerapi.reporter.ReporterComponent
import scala.concurrent.duration.{ DurationLong, FiniteDuration }
import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Failure, Success }

/**
 * Main Configuration
 */
trait MonitorConfiguration extends Configuration {
  lazy val timeout: Timeout = load { _.getDuration("powerapi.actors.timeout", TimeUnit.MILLISECONDS) } match {
    case ConfigValue(value) => Timeout(value.milliseconds)
    case _ => Timeout(15l.seconds)
  }
}

/**
 * One child represents one monitor.
 * Allows to publish messages in the right topics depending of the targets.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>        
 */
class MonitorChild(eventBus: MessageBus,
                   muid: UUID,
                   frequency: FiniteDuration,
                   targets: List[Target]) extends ActorComponent {

  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case MonitorStart(_, id, freq, targs) if muid == id && frequency == freq && targets == targs => start()
  } orElse default

  /**
   * Running state.
   */
  def running(aggR: AggregatePowerReport, aggFunction: Seq[Power] => Power): Actor.Receive = LoggingReceive {
    case tick: ClockTick => produceMessages(tick)
    case MonitorAggFunction(_, id, aggF) if muid == id => setAggregatingFunction(aggR, aggF)
    case powerReport: RawPowerReport => aggregate(aggR, powerReport, aggFunction)
    case GetMonitoredProcesses => sender ! targets
    case MonitorStop(_, id) if muid == id => stop()
    case _: MonitorStopAll => stop()
  } orElse default

  /**
   * Start the clock, subscribe on the associated topic for receiving tick messages
   * and power reports.
   */
  def start(): Unit = {
    startClock(frequency)(eventBus)
    subscribeClockTick(frequency)(eventBus)(self)
    subscribeRawPowerReport(muid)(eventBus)(self)
    log.info("monitor is started, muid: {}", muid)
    context.become(running(AggregatePowerReport(muid, SUM), SUM))
  }

  /**
   * Change the aggregating function for this monitor
   */
  def setAggregatingFunction(aggR: AggregatePowerReport, aggF: Seq[Power] => Power): Unit = {
    log.info("aggregating function is changed")
    context.become(running(aggR, aggF))
  }

  /**
   * Handle ticks for publishing the targets in the right topic.
   */
  def produceMessages(tick: ClockTick): Unit = {
    targets.foreach(target => publishMonitorTick(muid, target, tick)(eventBus))
  }
  
  /**
   * Wait to retrieve power reports of all targets from a same monitor to aggregate them
   * into once power report.
   */
  def aggregate(aggR: AggregatePowerReport, powerReport: RawPowerReport, aggF: Seq[Power] => Power): Unit = {
    aggR += powerReport
    if (aggR.size >= targets.size) {
      render(aggR)(eventBus)
      context.become(running(AggregatePowerReport(muid, aggF), aggF))
    }
    else
      context.become(running(aggR, aggF))
  }

  /**
   * Publish a request for stopping the clock which is responsible to produce the ticks at this frequency,
   * stop to listen ticks and power reports and kill the monitor actor.
   */
  def stop(): Unit = {
    stopClock(frequency)(eventBus)
    unsubscribeClockTick(frequency)(eventBus)(self)
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

  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case msg: MonitorStart => start(msg)
    case msg: MonitorAggFunction => setAggregatingFunction(msg)
    case msg: MonitorStop => stop(msg)
    case msg: MonitorStopAll => stopAll(msg)
    case GetMonitoredProcesses => getMonitoredProcesses
  } orElse default

  /**
   * Start a new monitor.
   *
   * @param msg: Message received for starting a monitor.
   */
  def start(msg: MonitorStart): Unit = {
    val name = formatMonitorChildName(msg.muid)
    val child = context.actorOf(Props(classOf[MonitorChild], eventBus, msg.muid, msg.frequency, msg.targets), name)
    child ! msg
    sender ! MonitorStarted
  }
  
  /**
   * Change the aggregating function for a given MonitorChild.
   *
   * @param msg: Message received for changing the aggregating function.
   */
  def setAggregatingFunction(msg: MonitorAggFunction): Unit = {
    val name = formatMonitorChildName(msg.muid)
    context.actorSelection(name) ! msg
  }

  /**
   * Stop a given monitor.
   *
   * @param msg: Message received for stopping a given monitor.
   */
  def stop(msg: MonitorStop): Unit = {
    val name = formatMonitorChildName(msg.muid)
    context.actorSelection(name) ! msg
    monitorStopped(msg.muid)(eventBus)
  }

  /**
   * Stop all monitor actors.
   *
   * @param msg: Message received for stopping all monitor actors.
   */
  def stopAll(msg: MonitorStopAll): Unit = {
    context.actorSelection("*") ! msg
    monitorAllStopped()(eventBus)
    context.become(receive)
  }
  
  /**
   * Allows to get the processes which are monitored by a power meter.
   */
  def getMonitoredProcesses: Unit = {
    val monitoredProcesses = Future.sequence(for (child <- context.children) yield {
      child.ask(GetMonitoredProcesses)(timeout).map(Success(_)).recover({
        case e => Failure(e)
      })
    }).map(_.collect {
      case Success(list) => list.asInstanceOf[List[Target]].toSet
    })
    sender ! monitoredProcesses
  }
}

/**
 * This class is an interface to interact with the event bus.
 */
class Monitor(eventBus: MessageBus, system: ActorSystem) extends PowerMonitoring {
  private var reporters = Array[ActorRef]()
  val muid = UUID.randomUUID()
  
  def apply(aggregator: Seq[Power] => Power): this.type = {
    setAggFunction(muid, aggregator)(eventBus)
    this
  }
  
  def to(output: PowerDisplay): this.type = {
    val reporterRef = system.actorOf(Props(classOf[ReporterComponent], output))
    reporters :+= reporterRef
    subscribeAggPowerReport(muid)(eventBus)(reporterRef)
    this
  }

  def to(reference: ActorRef): this.type = {
    subscribeAggPowerReport(muid)(eventBus)(reference)
    this
  }

  def to(reference: ActorRef, subscribeMethod: MessageBus => ActorRef => Unit): this.type = {
    subscribeMethod(eventBus)(reference)
    this
  }

  def cancel(): Unit = {
    stopMonitor(muid)(eventBus)
    reporters foreach system.stop
    reporters = Array()
  }
}
