/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2016 Inria, University of Lille 1.
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
 *
 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */
package org.powerapi.reporter

import java.util.UUID

import akka.actor.ActorRef

import org.powerapi.PowerDisplay
import org.powerapi.core.{Channel, Message, MessageBus}

/**
  * Base channel for the Reporter actors.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
object ReporterChannel extends Channel {

  type M = ReporterMessage
  private val topic = "reporter:handling"

  /**
    * Used to subscribe to ReporterMessage on the right topic.
    */
  def subscribeReporterChannel: MessageBus => ActorRef => Unit = {
    subscribe(topic)
  }

  /**
    * Used to interact with the Supervisor.
    */
  def startReporter(muid: UUID, display: PowerDisplay): MessageBus => Unit = {
    publish(ReporterStart(topic, muid, display))
  }

  def stopReporter(muid: UUID): MessageBus => Unit = {
    publish(ReporterStop(topic, muid))
  }

  def stopAllReporter: MessageBus => Unit = {
    publish(ReporterStopAll(topic))
  }

  /**
    * Used to format the Reporter actor name.
    */
  def formatReporterName(output: PowerDisplay, muid: UUID): String = {
    s"_${output.getClass.getSimpleName.toLowerCase}_${muid}_"
  }

  trait ReporterMessage extends Message

  case class ReporterStart(topic: String, muid: UUID, output: PowerDisplay) extends ReporterMessage

  case class ReporterStop(topic: String, muid: UUID) extends ReporterMessage

  case class ReporterStopAll(topic: String) extends ReporterMessage

}
