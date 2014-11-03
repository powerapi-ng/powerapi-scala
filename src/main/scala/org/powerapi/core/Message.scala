package org.powerapi.core

import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.event.LookupClassification

/**
 * Reports are the base messages exchanged between PowerAPI components.
 */
trait Report {
  /**
   * A report is associated with a subscription unique identifier (SUID), which is at the origin of the report flow.
   */
  def suid: Long
  /**
   * A report is associated with a topic which is used to route the messages on the bus.
   */
  def topic: String
}

trait EventBus extends akka.event.EventBus {
  type Event = Report
  type Classifier = String
  type Subscriber = ActorRef
}

/**
 * Common event bus used by PowerAPI components to communicate.
 */
class LookupBus extends EventBus with LookupClassification {
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

/**
 * Initializing the event bus.
 */
object LookupBus {
  val eventBus = new LookupBus
}

class Channel {
  type Event = Report
  type Classifier = String
  type Subscriber = ActorRef

  def subscribe(topic: String)(bus: EventBus)(subscriber: ActorRef) = {
    bus.subscribe(subscriber, topic)
  }

  def publish(bus: EventBus)(report: Report) = {
    bus.publish(report)
  }
}

/**
 * Clock channel and messages.
 */
object ClockChannel extends Channel {
  /**
   * ClockTick is represented as a dedicated type of report.
   * 
   * @param suid: subscription UID of the report.
   * @param topic: subject used for routing the message.
   * @param frequency: clock frequency.
   */
  case class ClockTick(suid: Long,
                       topic: String,
                       frequency: FiniteDuration,
                       timestamp: Long = System.currentTimeMillis) extends Report

  /**
   * Messages.
   */
  case class StartClock(frequency: FiniteDuration, report: Report)
  case class StopClock(frequency: FiniteDuration)
  
  object StopAllClocks

  object OK

  val topic = "tick:subscription"

  def subscribe: EventBus => ActorRef => Unit = subscribe(topic)

  def formatTopicFromFrequency(frequency: FiniteDuration) = {
    val nanoSecs = frequency.toNanos
    s"tick:$nanoSecs"
  }
}

/**
 * Used to inject the bus for different actors.
 */
trait ClockChannel {
  import ClockChannel.{ ClockTick, publish, subscribe }

  // Null for compilation purpose
  def subscribeOnBus: ActorRef => Unit = subscribe(LookupBus.eventBus)
  def publishOnBus: ClockTick => Unit = publish(LookupBus.eventBus)
}