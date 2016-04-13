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

import scala.concurrent.duration.DurationInt

import akka.actor.SupervisorStrategy.{Directive, Resume}
import akka.actor.{Actor, ActorLogging, OneForOneStrategy, SupervisorStrategy, SupervisorStrategyConfigurator}

/**
  * Base trait for components which use Actor.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
trait ActorComponent extends Actor with ActorLogging {
  /**
    * Default behavior when a received message is unknown.
    */
  def default: Actor.Receive = {
    case unknown => throw new UnsupportedOperationException(s"unable to process message $unknown")
  }
}

/**
  * Base trait for API component.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
trait APIComponent extends ActorComponent

/**
  * Supervisor strategy.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
trait Supervisor extends ActorComponent {
  def handleFailure: PartialFunction[Throwable, Directive]

  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(10, 1.seconds)(handleFailure orElse SupervisorStrategy.defaultStrategy.decider)
}

/**
  * This class is used for defining a default supervisor strategy for the Guardian Actor.
  * The Guardian Actor is the main actor used when system.actorOf(...) is used.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class GuardianFailureStrategy extends SupervisorStrategyConfigurator {
  def create(): SupervisorStrategy = {
    OneForOneStrategy(10, 1.seconds)(handleFailure orElse SupervisorStrategy.defaultStrategy.decider)
  }

  def handleFailure: PartialFunction[Throwable, Directive] = {
    case _: UnsupportedOperationException => Resume
  }
}
