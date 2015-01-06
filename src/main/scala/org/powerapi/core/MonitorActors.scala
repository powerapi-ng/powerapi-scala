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
package org.powerapi.core

import java.util.UUID

import scala.concurrent.duration.FiniteDuration

import akka.actor.{ Actor, ActorRef, ActorSystem, PoisonPill, Props }
import akka.actor.SupervisorStrategy.{ Directive, Resume }
import akka.event.LoggingReceive

import org.powerapi.core.ClockChannel.ClockTick
import org.powerapi.module.PowerChannel.PowerReport
import org.powerapi.reporter.ReporterComponent

/**
 * One child represents one monitor.
 * Allows to publish messages in the right topics depending of the targets.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 * @author Loïc Huertas <l.huertas.pro@gmail.com>
 */
class MonitorChild(eventBus: MessageBus,
                   muid: UUID,
                   frequency: FiniteDuration,
                   targets: List[Target],
                   aggFunction: List[PowerReport] => PowerReport) extends ActorComponent {

  import org.powerapi.core.ClockChannel.{startClock, stopClock, subscribeClockTick, unsubscribeClockTick}
  import org.powerapi.core.MonitorChannel.{MonitorStart, MonitorStop, MonitorStopAll, publishMonitorTick}
  import org.powerapi.module.PowerChannel.{ subscribePowerReport, unsubscribePowerReport }
  import org.powerapi.reporter.AggPowerChannel.render

  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case MonitorStart(_, id, freq, targs, agg) if muid == id && frequency == freq && targets == targs && aggFunction == agg => start()
  } orElse default

  /**
   * Running state.
   */
  def running(buffer: List[PowerReport]): Actor.Receive = LoggingReceive {
    case tick: ClockTick => produceMessages(tick)
    case powerReport: PowerReport => aggregate(buffer, powerReport)
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
    subscribePowerReport(muid)(eventBus)(self)
    log.info("monitor is started, muid: {}", muid)
    context.become(running(List.empty))
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
  def aggregate(buffer: List[PowerReport], powerReport: PowerReport): Unit = {
    val powerReportList = buffer :+ powerReport
    if (powerReportList.size == targets.size) {
      render(aggFunction(powerReportList))(eventBus)
      context.become(running(List.empty))
    }
    else
      context.become(running(powerReportList))
  }

  /**
   * Publish a request for stopping the clock which is responsible to produce the ticks at this frequency,
   * stop to listen ticks and power reports and kill the monitor actor.
   */
  def stop(): Unit = {
    stopClock(frequency)(eventBus)
    unsubscribeClockTick(frequency)(eventBus)(self)
    unsubscribePowerReport(muid)(eventBus)(self)
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
class Monitors(eventBus: MessageBus) extends Supervisor {
  import org.powerapi.core.MonitorChannel.{MonitorStart, MonitorStop, MonitorStopAll, formatMonitorChildName, stopAllMonitor, subscribeMonitorsChannel}

  override def preStart(): Unit = {
    subscribeMonitorsChannel(eventBus)(self)
    super.preStart()
  }

  override def postStop(): Unit = {
    context.actorSelection("*") ! stopAllMonitor
    super.postStop()
  }

  /**
   * MonitorChild actors can only launch exception if the message received is not handled.
   */
  def handleFailure: PartialFunction[Throwable, Directive] = {
    case _: UnsupportedOperationException => Resume 
  }

  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case msg: MonitorStart => start(msg)
  } orElse default

  /**
   * Running state.
   */
  def running: Actor.Receive = LoggingReceive {
    case msg: MonitorStart => start(msg)
    case msg: MonitorStop => stop(msg)
    case msg: MonitorStopAll => stopAll(msg)
  } orElse default

  /**
   * Start a new monitor.
   *
   * @param msg: Message received for starting a monitor.
   */
  def start(msg: MonitorStart): Unit = {
    val name = formatMonitorChildName(msg.muid)
    val child = context.actorOf(Props(classOf[MonitorChild], eventBus, msg.muid, msg.frequency, msg.targets, msg.aggFunction), name)
    child ! msg
    context.become(running)
  }

  /**
   * Stop a given monitor.
   *
   * @param msg: Message received for stopping a given monitor.
   */
  def stop(msg: MonitorStop): Unit = {
    val name = formatMonitorChildName(msg.muid)
    context.actorSelection(name) ! msg
  }

  /**
   * Stop all monitor actors.
   *
   * @param msg: Message received for stopping all monitor actors.
   */
  def stopAll(msg: MonitorStopAll): Unit = {
    context.actorSelection("*") ! msg
    context.become(receive)
  }
}

/**
 * This class is an interface for interacting directly with a MonitorChild actor.
 */
class Monitor(eventBus: MessageBus, _system: ActorSystem) {
  val muid = UUID.randomUUID()
  
  def reportTo(reporterComponent: Class[_ <: ReporterComponent], args: List[Any] = List()): Monitor = {
    reportTo(_system.actorOf(Props(reporterComponent, args: _*)))
  }
  def reportTo(reporter: ActorRef): Monitor = {
    import org.powerapi.reporter.AggPowerChannel.subscribeAggPowerReport
  
    subscribeAggPowerReport(muid)(eventBus)(reporter)
    this
  }
  
  def cancel(): Unit = {
    import org.powerapi.core.MonitorChannel.stopMonitor
    
    stopMonitor(muid)(eventBus)
  }
}
