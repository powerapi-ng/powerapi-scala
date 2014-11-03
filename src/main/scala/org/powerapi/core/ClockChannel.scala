package org.powerapi.core

import scala.concurrent.duration._

import akka.actor.ActorRef

/**
 * Clock channel and messages.
 */
object ClockChannel extends Channel {
  type R = ClockTick

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
  object NOK

  private val topic = "tick:subscription"

  def subscribe: EventBus => ActorRef => Unit = subscribe(topic)

  def formatTopicFromFrequency(frequency: FiniteDuration) = {
    new StringContext("tick:", "").s(frequency.toNanos)
  }
}

/**
 * Used to inject the bus for different actors.
 */
trait ClockChannel {
  import ClockChannel.{ ClockTick, publish, subscribe }

  def subscribeOnBus: ActorRef => Unit = subscribe(ReportBus.eventBus)
  def publishOnBus: ClockTick => Unit = publish(ReportBus.eventBus)
}