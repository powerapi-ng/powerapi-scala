/*
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
 *
 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */
package org.powerapi.core

import java.util.UUID

import akka.actor.ActorRef

/**
 * Reporter channel and messages.
 *
 * @author Loïc Huertas <l.huertas.pro@gmail.com>
 */
object ReporterChannel extends Channel {
  import org.powerapi.module.PowerChannel.PowerReport
  
  type M = ReporterMessage

  trait ReporterMessage extends Message

  /**
   * ReporterStart is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param muid: monitor unique identifier (MUID), which is at the origin of the report flow.
   * @param nbTarget: number of target required by a ReporterChild to trigger the aggregation function.
   * @param aggFunction: aggregate the PowerReports of a same monitor.
   */
  case class ReporterStart(topic: String, muid: UUID, nbTarget: Int, aggFunction: List[PowerReport] => Double) extends ReporterMessage

  /**
   * ReporterStop is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param muid: monitor unique identifier (MUID), which is at the origin of the report flow.
   */
  case class ReporterStop(topic: String, muid: UUID) extends ReporterMessage

  /**
   * ReporterStopAll is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   */
  case class ReporterStopAll(topic: String) extends ReporterMessage

  /**
   * Topic for communicating with the Reporters actor.
   */
  private val topic = "reporter:handling"

  /**
   * External Methods used by the API (or a Reporter object) for interacting with the bus.
   */
  def startReporter(muid: UUID, nbTarget: Int, aggFunction: List[PowerReport] => Double): MessageBus => Unit = {
    println("[START]" + muid)
    publish(ReporterStart(topic, muid, nbTarget, aggFunction))
  }

  def stopReporter(muid: UUID): MessageBus => Unit = {
    publish(ReporterStop(topic, muid))
  }

  /**
   * Internal methods used by the Reporters actor for interacting with the bus.
   */
  def subscribeReportersChannel: MessageBus => ActorRef => Unit = {
    subscribe(topic)
  }

  lazy val stopAllReporter = ReporterStopAll(topic)

  /**
   * Use to format the ReporterChild name.
   */
  def formatReporterChildName(muid: UUID): String = {
    s"reporter-$muid"
  }
}

