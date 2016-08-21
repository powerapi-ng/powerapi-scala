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
package org.powerapi.module.libpfm

import java.util.UUID

import scala.concurrent.Future

import akka.actor.ActorRef

import org.powerapi.core.target.Target
import org.powerapi.core.{Channel, Message, MessageBus, Tick}

/**
  * PerformanceCounterChannel channel and messages.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
object PerformanceCounterChannel extends Channel {

  type M = PCReport

  /**
    * Publish a PerformanceCounterReport in the event bus.
    */
  def publishPCReport(muid: UUID, target: Target, values: Map[Int, Map[String, Seq[HWCounter]]], tick: Tick): MessageBus => Unit = {
    publish(PCReport(pcReportToTopic(muid, target), muid, target, values, tick))
  }

  /**
    * Used to format the topic used to interact with the FormulaChild actors.
    */
  def pcReportToTopic(muid: UUID, target: Target): String = {
    s"libpfm-sensor:$muid-$target"
  }

  /**
    * Used to subscribe to PCReport on the right topic.
    */
  def subscribePCReport(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    subscribe(pcReportToTopic(muid, target))
  }

  /**
    * Used to unsubscribe to PCReport on the rigth topic.
    */
  def unsubscribePCReport(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    unsubscribe(pcReportToTopic(muid, target))
  }

  /**
    * Used to format the LibpfmCoreSensorChild names.
    *
    * BUG: We use special characters at the end of strings because there is a problem when we try to get an actor by its name otherwise.
    */
  def formatLibpfmCoreSensorChildName(core: Int, event: String, muid: UUID): String = {
    s"_${core}_${event.toLowerCase().replace('_', '-').replace(':', '-')}_${muid}_"
  }

  /**
    * Used to format the LibpfmCoreProcessSensorChild names
    *
    * BUG: We use special characters at the end of strings because there is a problem when we try to get an actor by its name otherwise.
    */
  def formatLibpfmCoreProcessSensorChildName(core: Int, event: String, muid: UUID, identifier: String): String = {
    s"_${core}_${event.toLowerCase().replace('_', '-').replace(':', '-')}_${muid}_${identifier}_"
  }

  /**
    * Internal message.
    * One message per core/event.
    */
  //case class PCWrapper(core: Int, event: String, values: Seq[Future[HWCounter]])

  /**
    * Internal message used to wrap a performance counter value for a period in ns.
    */
  case class HWCounter(value: Long)

  /**
    * PCReport is represented as a dedicated type of message.
    *
    * @param topic    : subject used for routing the message.
    * @param muid     : monitor unique identifier (MUID), which is at the origin of the report flow.
    * @param target   : monitor target.
    * @param values : performance counter values.
    * @param tick     : tick origin.
    */
  case class PCReport(topic: String,
                      muid: UUID,
                      target: Target,
                      values: Map[Int, Map[String, Seq[HWCounter]]],
                      tick: Tick) extends Message

  /**
    * Internal message used to stop a LibpfmPicker and clean its internal resources.
    */
  object LibpfmPickerStop

}
