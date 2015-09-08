/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2015 Inria, University of Lille 1.
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

import akka.actor.ActorRef
import java.util.UUID
import org.powerapi.core.{Channel, MessageBus}
import org.powerapi.core.ClockChannel.ClockTick
import org.powerapi.core.target.Target
import org.powerapi.module.SensorChannel.SensorReport
import scala.concurrent.Future

/**
 * PerformanceCounterChannel channel and messages.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
object PerformanceCounterChannel extends Channel {
  type M = org.powerapi.module.SensorChannel.M

  /**
   * Internal message.
   * One message per core/event.
   * Values is a list of performance counter values for each element of a core.
   */
  case class PCWrapper(core: Int, event: String, values: List[Future[Long]]) {
    def +(value: Future[Long]): PCWrapper = {
      PCWrapper(core, event, values :+ value)
    }
  }

  /**
   * PCReport is represented as a dedicated type of message.
   *
   * @param topic: subject used for routing the message.
   * @param muid: monitor unique identifier (MUID), which is at the origin of the report flow.
   * @param target: monitor target.
   * @param wrappers: performance counter wrappers.
   * @param tick: tick origin.
   */
  case class PCReport(topic: String,
                      muid: UUID,
                      target: Target,
                      wrappers: List[PCWrapper],
                      tick: ClockTick) extends SensorReport

  /**
   * Topic for communicating with the Formula actors.
   */
  private val topic = "sensor:libpfm-core"

  /**
   * Publish a PerformanceCounterReport in the event bus.
   */
  def publishPCReport(muid: UUID, target: Target, wrappers: List[PCWrapper], tick: ClockTick): MessageBus => Unit = {
    publish(PCReport(topic = topic,
                     muid = muid,
                     target = target,
                     wrappers = wrappers,
                     tick = tick))
  }

  /**
   * External method used by the Formula for interacting with the bus.
   */
  def subscribePCReport: MessageBus => ActorRef => Unit = {
    subscribe(topic)
  }

  /**
   * Use to format the names.
   *
   * BUG: We use special characters at the end of strings because there is a problem when we try to get an actor by its
   * name otherwise (string truncated).
   */
  def formatLibpfmPickerNameForCore(core: Int, event: String, muid: UUID): String = {
    s"_${core}_${event.toLowerCase().replace('_', '-').replace(':', '-')}_${muid}_"
  }

  def formatLibpfmPickerNameForCoreProcess(core: Int, event: String, muid: UUID, pid: Int): String = {
    s"_${core}_${event.toLowerCase().replace('_', '-').replace(':', '-')}_${muid}_${pid}_"
  }

  def formatLibpfmPickerNameForCoreMethod(core: Int, event: String, muid: UUID, label: String): String = {
    s"_${core}_${event.toLowerCase().replace('_', '-').replace(':', '-')}_${muid}_${label}_"
  }
}
