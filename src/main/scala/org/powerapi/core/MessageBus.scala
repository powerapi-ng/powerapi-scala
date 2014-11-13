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

import akka.actor.ActorRef
import akka.event.LookupClassification

/**
 * Messages are the messages used to route the messages in the bus.
 */
trait Message {
  /**
   * A message is associated with a topic which is used to route the messages on the bus.
   */
  def topic: String
}

/**
 * Reports are the base messages exchanged between PowerAPI components.
 */
trait Report extends Message {
  /**
   * A report is associated with a subscription unique identifier (SUID), which is at the origin of the report flow.
   */
  def suid: String
}

trait EventBus extends akka.event.EventBus {
  type Event = Message
  type Classifier = String
  type Subscriber = ActorRef
}

/**
 * Common event bus used by PowerAPI components to communicate.
 */
class MessageBus extends EventBus with LookupClassification {
  // is used for extracting the classifier from the incoming events
  override protected def classify(event: Event): Classifier = event.topic
  
  // will be invoked for each event for all subscribers which registered themselves
  // for the event’s classifier
  override protected def publish(event: Event, subscriber: Subscriber): Unit = {
    subscriber ! event
  }
  
  // must define a full order over the subscribers, expressed as expected from
  // `java.lang.Comparable.compare`
  override protected def compareSubscribers(a: Subscriber, b: Subscriber): Int =
    a.compareTo(b)
  
  // determines the initial size of the index data structure
  // used internally (i.e. the expected number of different classifiers)
  override protected def mapSize: Int = 2048
}

/**
 * Initializing the event bus.
 */
object MessageBus {
  val eventBus = new MessageBus
}

/**
 * Used to specify the channels used by the components.
 */
class Channel {
  type M <: Message

  def subscribe(bus: EventBus, topic: String)(subscriber: ActorRef) = {
    bus.subscribe(subscriber, topic)
  }

  def unsubscribe(bus: EventBus, topic: String)(subscriber: ActorRef) = {
    bus.unsubscribe(subscriber, topic)
  }

  def publish(bus: EventBus, message: M) = {
    bus.publish(message)
  }
}
