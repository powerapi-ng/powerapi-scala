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
package org.powerapi.module.powerspy

import akka.util.Timeout
import org.powerapi.core.{OSHelper, MessageBus}
import org.powerapi.module.SensorComponent

trait Configuration extends org.powerapi.core.Configuration {
  import org.powerapi.core.ConfigValue

  lazy val sppUrl = load { _.getString("powerapi.powerspy.spp-url") } match {
    case ConfigValue(url) => url
    case _ => "btspp://nothing"
  }

  lazy val version = load { _.getInt("powerapi.powerspy.version") } match {
    case ConfigValue(2) => PowerSpyVersion.POWERSPY_V2
    case _ => PowerSpyVersion.POWERSPY_V1
  }
}

class PowerSpySensor(eventBus: MessageBus, osHelper: OSHelper, timeout: Timeout) extends SensorComponent(eventBus) with Configuration {
  import akka.actor.{Actor, ActorRef}
  import akka.event.LoggingReceive
  import akka.pattern.gracefulStop
  import org.powerapi.core.MonitorChannel.MonitorTick
  import org.powerapi.core.{All, Application, Process, Target, TargetUsageRatio}
  import org.powerapi.module.powerspy.PSpyMetricsChannel.{PSpyChildMessage, PSpyStart, publishPSpyDataReport}

  override def postStop() = {
    gracefulStop(pspyChild, timeout.duration)
    super.postStop()
  }

  lazy val childProps = PowerSpyChild.props(sppUrl, version)
  lazy val pspyChild: ActorRef = childProps match {
    case Some(props) => context.actorOf(props, "pspy-child")
    case _ => log.error("the PowerSpy ({}) is not reachable", sppUrl); null
  }

  /**
   * The default behavior is overridden because this sensor handles other messages
   * and it has different states.
   */
  override def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case msg: MonitorTick => start()
  } orElse default

  def start(): Unit = {
    pspyChild ! PSpyStart
    context.become(running(List()))
  }

  def running(messages: List[PSpyChildMessage]): Actor.Receive = LoggingReceive {
    case msg: MonitorTick => sense(msg, messages)
    case msg: PSpyChildMessage => context.become(running(messages :+ msg))
  } orElse default

  def sense(monitorTick: MonitorTick, messages: List[PSpyChildMessage]): Unit = {
    PSpyChildMessage.avg(messages) match {
      case Some(avg) => {
        monitorTick.target match {
          case process: Process => {
            publishPSpyDataReport(monitorTick.muid, monitorTick.target, getCpuUsage(process), avg.rms, avg.uScale, avg.iScale, monitorTick.tick)(eventBus)
          }
          case application: Application => {
            publishPSpyDataReport(monitorTick.muid, monitorTick.target, getCpuUsage(application), avg.rms, avg.uScale, avg.iScale, monitorTick.tick)(eventBus)
          }
          case All => {
            publishPSpyDataReport(monitorTick.muid, monitorTick.target, avg.rms, avg.uScale, avg.iScale, monitorTick.tick)(eventBus)
          }
        }

        context.become(running(List()))
      }
      case _ => log.debug("no powerspy messages received")
    }
  }

  private def getCpuUsage(target: Target): TargetUsageRatio = {
    lazy val globalTime = osHelper.getGlobalCpuTime() match {
      case Some(time) => time
      case _ => 1 // we cannot divide by 0
    }

    lazy val targetTime = target match {
      case process: Process => osHelper.getProcessCpuTime(process) match {
        case Some(time) => time
        case _ => 0l
      }
      case application: Application => osHelper.getProcesses(application).foldLeft(0l) { (acc, process) =>
        osHelper.getProcessCpuTime(process) match {
          case Some(time) => time
          case _ => 0l
        }
      }
      case _ => 0l
    }

    TargetUsageRatio(targetTime / globalTime)
  }

  /**
   * Not used here, the default behavior is overridden.
   */
  def sense(monitorTick: MonitorTick): Unit = {}
}
