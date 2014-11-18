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

import akka.actor.ActorRef

/**
 * Subscription channel and messages.
 */
object SubscriptionChannel extends Channel {
  
  type M = SubscriptionMessage

  trait SubscriptionMessage extends Message

  /**
   * SubscriptionTarget is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param suid: subscription unique identifier (SUID), which is at the origin of the report flow.
   * @param target: monitoring target.
   */
  case class SubscriptionTarget(topic: String,
                                suid: UUID,
                                target: Target) extends SubscriptionMessage with Report

  /**
   * SubscriptionStart is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param suid: subscription unique identifier (SUID), which is at the origin of the report flow.
   * @param frequency: clock frequency.
   * @param targets: monitoring targets.
   */
  case class SubscriptionStart(topic: String,
                               suid: UUID,
                               frequency: FiniteDuration,
                               targets: List[Target]) extends SubscriptionMessage

  /**
   * SubscriptionStop is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param suid: subscription unique identifier (SUID), which is at the origin of the report flow.
   */
  case class SubscriptionStop(topic: String, suid: UUID) extends SubscriptionMessage

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
   * Topic for communicating with the Sensor.
   */
  private val topicToPublish = "subscription:target"

  /**
   * Methods used to interact with the subscription actors by using the bus.
   */
  def subscribeTarget: MessageBus => ActorRef => Unit = {
    subscribe(topicToPublish) _
  }

  def startSubscription(suid: UUID, frequency: FiniteDuration, targets: List[Target]): MessageBus => Unit = {
    publish(SubscriptionStart(topic, suid, frequency, targets)) _
  }

  def stopSubscription(suid: UUID): MessageBus => Unit = {
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

  def publishTarget(suid: UUID, target: Target): MessageBus => Unit = {
    publish(SubscriptionTarget(topicToPublish, suid, target)) _
  }

  lazy val stopAllSubscriptions = SubscriptionStopAll(topic)

  /**
   * Use to format the subscription child name.
   */
  def formatSubscriptionChildName(suid: UUID) = {
    s"subscription-$suid"
  }
}
