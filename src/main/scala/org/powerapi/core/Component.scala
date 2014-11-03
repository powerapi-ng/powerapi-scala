package org.powerapi.core

import akka.actor.{ Actor, ActorLogging }
import akka.event.LoggingReceive

/**
 * Base trait for components which use Actor.
 */
trait Component extends Actor with ActorLogging {
  /**
   * Receive wrapper.
   */
  def acquire: Actor.Receive

  def default: Actor.Receive = {
    case unknown => throw new UnsupportedOperationException(s"unable to process message $unknown")
  }

  def receive = LoggingReceive {
    acquire orElse default
  }
}