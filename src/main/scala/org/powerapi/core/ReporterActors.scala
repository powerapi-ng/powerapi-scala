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

import scala.concurrent.duration.{ Duration, DurationInt }

import akka.actor.{ Actor, ActorSystem, PoisonPill, Props }
import akka.actor.SupervisorStrategy.{ Directive, Resume }
import akka.event.LoggingReceive
import akka.pattern.gracefulStop

import org.powerapi.module.PowerChannel.PowerReport
import org.powerapi.reporter.ReporterComponent

/**
 * One child represents one reporter.
 * Allows to publish messages in the right topics depending of the targets.
 *
 * @author Loïc Huertas <l.huertas.pro@gmail.com>
 */
class ReporterChild(eventBus: MessageBus, muid: UUID, nbTarget: Int, aggFunction: List[PowerReport] => Double) extends ActorComponent {
  import org.powerapi.module.PowerChannel.{ subscribePowerReport, unsubscribePowerReport }
  import org.powerapi.core.ReporterChannel.{ ReporterStart, ReporterStop, ReporterStopAll }
  import org.powerapi.reporter.AggPowerChannel.publishAggPowerReport

  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case ReporterStart(_, id, _, _) if muid == id => start()
  } orElse default

  /**
   * Running state.
   */
  def running(buffer: List[PowerReport]): Actor.Receive = LoggingReceive {
    case powerReport: PowerReport => produceAggPowerReports(buffer, powerReport)
    case ReporterStop(_, id) if muid == id => stop()
    case _: ReporterStopAll => stop()
  } orElse default

  /**
   * Subscribe on the associated topic for receiving powerReport messages.
   */
  def start(): Unit = {
    subscribePowerReport(muid)(eventBus)(self)
    log.info("reporter is started, muid: {}", muid)
    context.become(running(List.empty))
  }

  /**
   * Handle ticks for publishing the targets in the right topic.
   */
  def produceAggPowerReports(buffer: List[PowerReport], powerReport: PowerReport): Unit = {
    val powerReportList = buffer :+ powerReport
    if (powerReportList.size == nbTarget) {
      publishAggPowerReport(muid, powerReport.target, aggFunction(powerReportList), powerReport.unit, powerReport.device, powerReport.tick)(eventBus)
      context.become(running(List.empty))
    }
    else
      context.become(running(powerReportList))
  }

  /**
   * Stop to listen powerReport messages and kill the reporter actor.
   */
  def stop(): Unit = {
    unsubscribePowerReport(muid)(eventBus)(self)
    log.info("reporter is stopped, muid: {}", muid)
    self ! PoisonPill
  }
}

/**
 * This actor listens the bus on a given topic and reacts on the received messages.
 * It is responsible to handle a pool of child actors which represent all reporters.
 *
 * @author Loïc Huertas <l.huertas.pro@gmail.com>
 */
class Reporters(eventBus: MessageBus) extends Supervisor {
  import org.powerapi.core.ReporterChannel.{ ReporterStart, ReporterStop, ReporterStopAll, formatReporterChildName, stopAllReporter, subscribeReportersChannel }

  override def preStart(): Unit = {
    subscribeReportersChannel(eventBus)(self)
  }

  override def postStop(): Unit = {
    context.actorSelection("*") ! stopAllReporter
  }

  /**
   * ReporterChild actors can only launch exception if the message received is not handled.
   */
  def handleFailure: PartialFunction[Throwable, Directive] = {
    case _: UnsupportedOperationException => Resume 
  }

  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case msg: ReporterStart => start(msg)
  } orElse default

  /**
   * Running state.
   */
  def running: Actor.Receive = LoggingReceive {
    case msg: ReporterStart => start(msg)
    case msg: ReporterStop => stop(msg)
    case msg: ReporterStopAll => stopAll(msg)
  } orElse default

  /**
   * Start a new reporter.
   *
   * @param msg: Message received for starting a reporter.
   */
  def start(msg: ReporterStart): Unit = {
    val name = formatReporterChildName(msg.muid)
    val child = context.actorOf(Props(classOf[ReporterChild], 
                                      eventBus, msg.muid, msg.nbTarget, msg.aggFunction), name)
    child ! msg
    context.become(running)
  }

  /**
   * Stop a given reporter.
   *
   * @param msg: Message received for stopping a given reporter.
   */
  def stop(msg: ReporterStop): Unit = {
    val name = formatReporterChildName(msg.muid)
    context.actorSelection(name) ! msg
  }

  /**
   * Stop all reporter actors.
   *
   * @param msg: Message received for stopping all reporter actors.
   */
  def stopAll(msg: ReporterStopAll): Unit = {
    context.actorSelection("*") ! msg
    context.become(receive)
  }
}

/**
 * Reporter contract.
 *
 * A reporter is responsible for rendering the power or energy reports.
 *
 * @author Loïc Huertas <l.huertas.pro@gmail.com>
 */
class Reporter(eventBus: MessageBus, _system: ActorSystem,
               aggFunction: List[PowerReport] => Double,
               reporterComponent: Class[_ <: ReporterComponent], args: List[Any] = List()) {
  
  private val reporterCompRef = _system.actorOf(Props(reporterComponent, args: _*))

  def attach(monitor: Monitor): Reporter = {
    //TODO check duplication of muid
    import org.powerapi.core.ReporterChannel.startReporter
    import org.powerapi.reporter.AggPowerChannel.subscribeAggPowerReport
  
    startReporter(monitor.muid, monitor.targets.size, aggFunction)(eventBus)
    subscribeAggPowerReport(monitor.muid)(eventBus)(reporterCompRef)
    this
  }
  
  def detach(monitor: Monitor): Reporter = {
    //TODO check existence of muid
    import org.powerapi.core.ReporterChannel.stopReporter
    import org.powerapi.reporter.AggPowerChannel.unsubscribeAggPowerReport
  
    stopReporter(monitor.muid)(eventBus)
    unsubscribeAggPowerReport(monitor.muid)(eventBus)(reporterCompRef)
    this
  }
  
  def cancel(): Unit = {
    //TODO list of all attached muid
    gracefulStop(reporterCompRef, 1.second)
  }
}

