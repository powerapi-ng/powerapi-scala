package org.powerapi.core

import scala.concurrent.duration._
import akka.util.Timeout

/**
 * Default actor's behavior on errors.
 */
trait DefaultActorBehavior {
  def messageUnsupported(unknown: Any) = {
    throw new UnsupportedOperationException("unable to process message $unknown")
  }

  def operationUnsupported(msg: String) = {
    throw new UnsupportedOperationException(msg)
  }
}

/**
 * Default Timeout.
 */
object DefaultTimeout {
  val timeout = Timeout(50.milliseconds)
}