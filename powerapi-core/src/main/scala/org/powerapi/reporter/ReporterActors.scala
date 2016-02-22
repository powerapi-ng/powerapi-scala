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
package org.powerapi.reporter

import java.util.UUID

import akka.actor.SupervisorStrategy.{Directive, Resume}
import akka.actor.{Actor, PoisonPill, Props}

import org.powerapi.PowerDisplay
import org.powerapi.core.{APIComponent, MessageBus, Supervisor}
import org.powerapi.module.PowerChannel.{AggregatePowerReport, subscribeAggPowerReport, unsubscribeAggPowerReport}
import org.powerapi.reporter.ReporterChannel.{ReporterStart, ReporterStop, ReporterStopAll, formatReporterName, subscribeReporterChannel}

/**
  * Reporter actor created per muid/display to reacts to AggPowerReport.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class Reporter(eventBus: MessageBus, muid: UUID, output: PowerDisplay) extends APIComponent {

  def receive: Actor.Receive = starting orElse default

  def starting: Actor.Receive = {
    case msg: ReporterStart if msg.muid == muid && msg.output == output => start()
  }

  /**
    * Start the reporter.
    */
  def start(): Unit = {
    subscribeAggPowerReport(muid)(eventBus)(self)
    log.info("reporter is started, class: {}, muid: {}", output.getClass.getName, muid)
    context.become(running orElse default)
  }

  def running: Actor.Receive = {
    case msg: AggregatePowerReport => output.display(msg.muid, msg.tick.timestamp, msg.targets, msg.devices, msg.power)
    case msg: ReporterStop if msg.muid == muid => stop()
    case _: ReporterStopAll => stop()
  }

  /**
    * Stop the reporter.
    */
  def stop(): Unit = {
    unsubscribeAggPowerReport(muid)(eventBus)(self)
    log.info("reporter is stopped, class: {}, muid: {}", output.getClass.getName, muid)
    self ! PoisonPill
  }
}

/**
  * This Reporter supervisor listens the bus and reacts on the received messages.
  * It is responsible to handle a pool of reporters.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class Reporters(eventBus: MessageBus) extends Supervisor {

  override def preStart(): Unit = {
    subscribeReporterChannel(eventBus)(self)
    super.preStart()
  }

  /**
    * Reporter actors can only launch exception if the message received is not handled.
    */
  def handleFailure: PartialFunction[Throwable, Directive] = {
    case _: UnsupportedOperationException => Resume
  }

  def receive: Actor.Receive = running orElse default

  def running: Actor.Receive = {
    case msg: ReporterStart => start(msg)
    case msg: ReporterStop => stop(msg)
    case msg: ReporterStopAll => stopAll(msg)
  }

  /**
    * Start a new Reporter for a given muid and a given PowerDisplay.
    *
    * @param msg Message received for starting a Reporter.
    */
  def start(msg: ReporterStart): Unit = {
    val name = formatReporterName(msg.output, msg.muid)

    val child = context.child(name) match {
      case None =>
        val child = context.actorOf(Props(classOf[Reporter], eventBus, msg.muid, msg.output), name)
        child ! msg
      case _ =>
        log.warning("this reporter is started already, class: {}, muid, {}", msg.output.getClass.getName, msg.muid)
    }
  }

  /**
    * Stop all reporters associated to a given muid.
    *
    * @param msg Message received for stopping reporters created for a given muid.
    */
  def stop(msg: ReporterStop): Unit = {
    context.actorSelection(s"*${msg.muid}*") ! msg
  }

  /**
    * Stop all reporters.
    *
    * @param msg Message received for stopping all reporters.
    */
  def stopAll(msg: ReporterStopAll): Unit = {
    context.actorSelection("*") ! msg
  }
}
