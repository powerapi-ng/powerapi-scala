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
package org.powerapi.module.disk

import java.util.UUID

import akka.actor.ActorRef

import org.powerapi.core.target.{Target, TargetUsageRatio}
import org.powerapi.core.{Disk, Channel, Message, MessageBus, Tick, TimeInStates}

/**
  * UsageMetricsChannel channel and messages.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
object UsageMetricsChannel extends Channel {

  type M = DiskUsageReport

  /**
    * Publish a DiskUsageReport in the event bus.
    */
  def publishDiskUsageReport(muid: UUID, target: Target, usages: Seq[TargetDiskUsageRatio], tick: Tick): MessageBus => Unit = {
    publish(DiskUsageReport(diskUsageReportTopic(muid, target), muid, target, usages, tick))
  }

  /**
    * Used to format the topic used to interact with the FormulaChild actors.
    */
  def diskUsageReportTopic(muid: UUID, target: Target): String = {
    s"disk-simple-sensor:$muid-$target"
  }

  /**
    * Used to subscribe to DiskUsageReport on the right topic.
    */
  def subscribeDiskUsageReport(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    subscribe(diskUsageReportTopic(muid, target))
  }

  /**
    * Used to unsubscribe to DiskUsageReport on the right topic.
    */
  def unsubscribeDiskUsageReport(muid: UUID, target: Target): MessageBus => ActorRef => Unit = {
    unsubscribe(diskUsageReportTopic(muid, target))
  }

  case class TargetDiskUsageRatio(name: String, bytesRead: Long, readRatio: Double, bytesWritten: Long, writeRatio: Double)

  /**
    * DiskUsageReport is represented as a dedicated type of message.
    *
    * @param topic   subject used for routing the message.
    * @param muid    monitor unique identifier (MUID), which is at the origin of the report flow.
    * @param target  monitor target.
    * @param usages  target disk usages.
    * @param tick    tick origin.
    */
  case class DiskUsageReport(topic: String,
                             muid: UUID,
                             target: Target,
                             usages: Seq[TargetDiskUsageRatio],
                             tick: Tick) extends Message

}
