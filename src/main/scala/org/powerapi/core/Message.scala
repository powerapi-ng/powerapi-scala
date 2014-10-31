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

class Channel {
    def subscribe(bus: EventBus)(topic:String)(subscriber: ActorRef)
    def publish[R<:Report](bus: EventBus)(report: R)
}

object PowerChannel extends Channel[Power] {
  /**
   * Power is represented as a dedicated type of report.
   * 
   * @param suid: subscription UID of the report.
   * @param power: raw value of the power consumption.
   * @param unit: unit used by the power consumption.
   * @param rate: sampling rate of the power consumption.
   */
  object PowerUnit extends Enumeration {
      val W, kW = Value
  }
  case class Power(override val suid: Long,
                   power: Double,
                   unit: PowerUnit
                   rate: Duration) extends Report(suid)
  def subscribe(bus: EventBus)(subscriber: ActorRef)
  def publish(bus: EventBus)(report: Power)
}
