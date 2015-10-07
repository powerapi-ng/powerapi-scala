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
package org.powerapi.module.extPMeter

import akka.actor.ActorRef
import org.powerapi.core.{Channel, Message, MessageBus}
import org.powerapi.core.power.Power

/**
 * ExtPMeterChannel channel and messages.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
 */
object ExtPMeterChannel extends Channel {

  type M = ExtPMeterPower

  /**
   * ExtPMeterPower is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param power: power consumption got by an external device.
   */
  case class ExtPMeterPower(topic: String,
                            power: Power) extends Message

  /**
   * Topic for communicating with the Sensor actor.
   */
  private val pMeterTopic = "extpmeter:power"

  /**
   * Topic for communicating with the Formula actor.
   */
  private val topic = "sensor:extpmeter"

  /**
   * Publish a ExtPMeterPower in the event bus.
   */
  def publishExternalPMeterPower(power: Power): MessageBus => Unit = {
    publish(ExtPMeterPower(pMeterTopic, power))
  }

  def publishPMeterPower(power: Power): MessageBus => Unit = {
    publish(ExtPMeterPower(topic, power))
  }

  /**
   * External methods used for interacting with the bus.
   */
  def subscribeExternalPMeterPower: MessageBus => ActorRef => Unit = {
    subscribe(pMeterTopic)
  }

  def subscribePMeterPower: MessageBus => ActorRef => Unit = {
    subscribe(topic)
  }
}
