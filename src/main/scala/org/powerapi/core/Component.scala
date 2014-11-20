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

import akka.actor.SupervisorStrategy.{Directive, Resume}
import akka.actor._
import akka.event.LoggingReceive

import scala.concurrent.duration.DurationInt

/**
 * Base trait for components which use Actor.
 *
 * @author mcolmant
 */
trait Component extends Actor with ActorLogging {
  /**
   * Default behavior when a received message is unknown.
   */
  def default: Actor.Receive = {
    case unknown => throw new UnsupportedOperationException(s"unable to process message $unknown")
  }
}

/**
 * Base trait for each PowerAPI sensor.
 * Each of them should listen to a MonitorTarget message and thus process it.
 */
abstract class Sensor(eventBus: MessageBus) extends Component {
  import org.powerapi.core.MonitorChannel.{MonitorTarget, subscribeTarget}

  override def preStart(): Unit = {
    subscribeTarget(eventBus)(self)
  }

  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case msg: MonitorTarget => process(msg)
  } orElse default

  def process(monitorTarget: MonitorTarget): Unit
}

/**
 * Supervisor strategy.
 *
 * @author mcolmant
 */
trait Supervisor extends Component {
  def handleFailure: PartialFunction[Throwable, Directive]

  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(10, 1.seconds)(handleFailure orElse SupervisorStrategy.defaultStrategy.decider)
}

/**
 * This class is used for defining a default supervisor strategy for the Guardian Actor.
 * The Guardian Actor is the main actor used when system.actorOf(...) is used.
 *
 * @author mcolmant
 */
class GuardianFailureStrategy extends SupervisorStrategyConfigurator {
  def handleFailure: PartialFunction[Throwable, Directive] = {
    case _: UnsupportedOperationException => Resume
  }

  def create(): SupervisorStrategy = {
    OneForOneStrategy(10, 1.seconds)(handleFailure orElse SupervisorStrategy.defaultStrategy.decider)
  }
}
