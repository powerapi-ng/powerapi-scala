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

import scala.concurrent.duration.FiniteDuration

import akka.actor.{ Actor, PoisonPill }
import akka.event.LoggingReceive

/**
 * One child represents one monitoring.
 * Allows to publish messages in the right topics depending of the targets.
 * A subscription child is called by its suid for lookups.
 */
class SubscriptionChild(suid: String, frequency: FiniteDuration, targets: List[Target]) extends Component {
  import ClockChannel.{ ClockTick, startClock, stopClock, subscribeClock, unsubscribeClock }
  import SubscriptionChannel.{ publishProcess, publishApp, publishAll, SubscriptionStart, SubscriptionStop }

  def receive = LoggingReceive {
    case SubscriptionStart(_, id, freq, targs) if(suid == id && frequency == freq && targets == targs) => start()
  } orElse default

  /**
   * Running state.
   */
  def running: Actor.Receive = LoggingReceive {
    case _: ClockTick => produceMessages()
    case SubscriptionStop(_, id) if suid == id => stop()
  } orElse default

  /**
   * Start the clock, subscribe on the associated topic for receiving tick messages.
   */
  def start() = {
    startClock(frequency)
    subscribeClock(frequency)(self)
    log.info("subscription is started, suid: {}", suid)
    context.become(running)
  }

  /**
   * Handle ticks for publishing the targets in the right topics.
   */
  def produceMessages() = {
    targets.foreach(target => {
      target match {
        case process: Process => publishProcess(suid, process)
        case app: Application => publishApp(suid, app)
        case ALL => publishAll(suid)
      }
    })
  }

  /**
   * Publish a request for stopping the clock which is responsible to produce the ticks at this frequency,
   * stop to listen ticks and kill the subscription actor.
   */
  def stop() = {
    stopClock(frequency)
    unsubscribeClock(frequency)(self)
    log.info("subscription is stopped, suid: {}", suid)
    self ! PoisonPill
  }
}