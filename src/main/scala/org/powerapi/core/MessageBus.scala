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
  override protected def mapSize(): Int = 256
}

/**
 * Used to specify the channels used by the components.
 */
class Channel {
  type M <: Message

  protected def subscribe(topic: String)(bus: EventBus)(subscriber: ActorRef): Unit = {
    bus.subscribe(subscriber, topic)
  }

  protected def unsubscribe(topic: String)(bus: EventBus)(subscriber: ActorRef): Unit = {
    bus.unsubscribe(subscriber, topic)
  }

  protected def publish(message: M)(bus: EventBus): Unit = {
    bus.publish(message)
  }
}
