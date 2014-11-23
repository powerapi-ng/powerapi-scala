/**
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

 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */
package org.powerapi.core

import java.util.UUID

import akka.actor.SupervisorStrategy.{Directive, Resume}
import akka.actor.{Actor, PoisonPill, Props}
import akka.event.LoggingReceive
import org.powerapi.core.MonitorChannel.MonitorSubscription


/**
 * One child represents one monitor.
 * Allows to publish messages in the right topics depending of the targets.
 */
class MonitorChild(eventBus: MessageBus, subscription: MonitorSubscription) extends Component {

  import org.powerapi.core.ClockChannel.{ClockTick, startClock, stopClock, subscribeClock, unsubscribeClock}
  import org.powerapi.core.MonitorChannel.{MonitorStart, MonitorStop, MonitorStopAll, publishTargets}

  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case MonitorStart(_, subs) if subscription == subs => start()
  } orElse default

  /**
   * Running state.
   */
  def running: Actor.Receive = LoggingReceive {
    case ClockTick(_, _, timestamp) => produceMessages(timestamp)
    case MonitorStop(_, id) if subscription.muid == id => stop()
    case _: MonitorStopAll => stop()
  } orElse default

  /**
   * Start the clock, subscribe on the associated topic for receiving tick messages.
   */
  def start(): Unit = {
    startClock(subscription.frequency)(eventBus)
    subscribeClock(subscription.frequency)(eventBus)(self)
    log.info("monitor is started, muid: {}", subscription.muid)
    context.become(running)
  }

  /**
   * Handle ticks for publishing the targets in the right topic.
   */
  def produceMessages(timestamp: Long): Unit = {
    publishTargets(subscription, timestamp)(eventBus)
  }

  /**
   * Publish a request for stopping the clock which is responsible to produce the ticks at this frequency,
   * stop to listen ticks and kill the monitor actor.
   */
  def stop(): Unit = {
    stopClock(subscription.frequency)(eventBus)
    unsubscribeClock(subscription.frequency)(eventBus)(self)
    log.info("monitor is stopped, muid: {}", subscription.muid)
    self ! PoisonPill
  }
}

/**
 * This actor listens the bus on a given topic and reacts on the received messages.
 * It is responsible to handle a pool of child actors which represent all monitors.
 */
class Monitors(eventBus: MessageBus) extends Component with Supervisor {
  import org.powerapi.core.MonitorChannel.{MonitorStart, MonitorStop, MonitorStopAll, formatMonitorChildName, stopAllMonitor, subscribeHandlingMonitor}

  override def preStart(): Unit = {
    subscribeHandlingMonitor(eventBus)(self)
  }

  override def postStop(): Unit = {
    context.actorSelection("*") ! stopAllMonitor
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
    val name = formatMonitorChildName(msg.subscription.muid)
    val child = context.actorOf(Props(classOf[MonitorChild], eventBus, msg.subscription), name)
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
class Monitor(eventBus: MessageBus) {
  val muid = UUID.randomUUID()

  def cancel(): Unit = {
    import org.powerapi.core.MonitorChannel.stopMonitor
    
    stopMonitor(muid)(eventBus)
  }
}
