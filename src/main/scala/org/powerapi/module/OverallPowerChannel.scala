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
package org.powerapi.module

import org.powerapi.core.Channel

/**
 * OverallPowerChannel channel and messages.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
object OverallPowerChannel extends Channel {
  import akka.actor.ActorRef
  import org.powerapi.core.{Message, MessageBus}
  import org.powerapi.module.PowerUnit.PowerUnit

  type M = OverallPower

  /**
   * OverallPower is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param power: overall power consumption.
   * @param unit: power unit.
   * @param source: origin of the overall power.
   */
  case class OverallPower(topic: String,
                          power: Double,
                          unit: PowerUnit,
                          source: String) extends Message

  /**
   * Topic for communicating with the OverallFormula actors.
   */
  private val topic = "power:overall"

  /**
   * Publish an OverallPower in the event bus.
   */
  def publishOverallPower(power: Double, unit: PowerUnit, source: String): MessageBus => Unit = {
    publish(OverallPower(topic, power, unit, source))
  }

  /**
   * External method used by the OverallFormula for interacting with the bus.
   */
  def subscribeOverallPower: MessageBus => ActorRef => Unit = {
    subscribe(topic)
  }
}
