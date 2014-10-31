package org.powerapi.core

import akka.event.EventBus
import akka.event.LookupClassification


/**
 * Reports are the base messages exchanged between PowerAPI components.
 *
 * @param suid: a report is associated with a subscription unique identifier (SUID), which is at the origin of the report flow.
 */
class Report(val topic: String, val suid: Long)

/**
 * Common event bus used by PowerAPI components to communicate.
 */
object LookupBus extends EventBus with LookupClassification {
  type Event = Report
  type Classifier = String
  type Subscriber = ActorRef
  
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
  override protected def mapSize: Int = 128
}
