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

import scala.concurrent.duration.FiniteDuration

import akka.actor.ActorRef

/**
 * Subscription channel and messages.
 */
object SubscriptionChannel extends Channel {
  
  type M = SubscriptionMessage

  trait SubscriptionMessage extends Message

  /**
   * SubscriptionProcess is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param suid: subscription unique identifier (SUID), which is at the origin of the report flow.
   * @param process: monitoring target.
   */
  case class SubscriptionProcess(topic: String,
                                 suid: String,
                                 process: Process) extends SubscriptionMessage with Report

  /**
   * SubscriptionApp is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param suid: subscription unique identifier (SUID), which is at the origin of the report flow.
   * @param app: monitoring target.
   */
  case class SubscriptionApp(topic: String,
                             suid: String,
                             app: Application) extends SubscriptionMessage with Report

  /**
   * SubscriptionAll is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param suid: subscription unique identifier (SUID), which is at the origin of the report flow.
   */
  case class SubscriptionAll(topic: String,
                             suid: String) extends SubscriptionMessage


  /**
   * SubscriptionStart is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param suid: subscription unique identifier (SUID), which is at the origin of the report flow.
   * @param frequency: clock frequency.
   * @param targets: monitoring targets.
   */
  case class SubscriptionStart(topic: String,
                               suid: String,
                               frequency: FiniteDuration,
                               targets: List[Target]) extends SubscriptionMessage

  /**
   * SubscriptionStop is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param suid: subscription unique identifier (SUID), which is at the origin of the report flow.
   */
  case class SubscriptionStop(topic: String, suid: String) extends SubscriptionMessage

  /**
   * SubscriptionStopAll is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   */
  case class SubscriptionStopAll(topic: String) extends SubscriptionMessage

  /**
   * Topic for communicating with the Subscription.
   */
  private val topic = "subscription:handling"

  /**
   * Topics for communicating with the Sensor.
   */
  private val topicProcess = "subscription:PID"
  private val topicAll = "subscription:ALL" 

  /**
   * Methods used by the sensor actors to interact with the subscription actors by
   * using the bus.
   */
  def subscribeProcess: MessageBus => ActorRef => Unit = {
    subscribe(topicProcess) _
  }

  def subscribeAll: MessageBus => ActorRef => Unit = {
    subscribe(topicAll) _
  }

  def startSubscription(suid: String, frequency: FiniteDuration, targets: List[Target]): MessageBus => Unit = {
    publish(SubscriptionStart(topic, suid, frequency, targets)) _
  }

  def stopSubscription(suid: String): MessageBus => Unit = {
    publish(SubscriptionStop(topic, suid)) _
  }

  def stopAllSubscription(): MessageBus => Unit = {
    publish(SubscriptionStopAll(topic)) _
  }

  /**
   * Methods used by the subscription actors to interact with the event bus.
   */
  def subscribeHandlingSubscription: MessageBus => ActorRef => Unit = {
    subscribe(topic) _
  }

  def publishProcess(suid: String, pid: Process): MessageBus => Unit = {
    publish(SubscriptionProcess(topicProcess, suid, pid)) _
  }

  def publishApp(suid: String, app: Application): MessageBus => Unit = {
    publish(SubscriptionApp(topicProcess, suid, app)) _
  }

  def publishAll(suid: String): MessageBus => Unit = {
    publish(SubscriptionAll(topicAll, suid)) _
  }

  def lastStopAllMessage() = {
    SubscriptionStopAll(topic)
  }
}
