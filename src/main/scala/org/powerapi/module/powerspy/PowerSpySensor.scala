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
  import org.powerapi.module.{Cache, CacheKey}
  import scala.reflect.ClassTag

  var pspyChild: Option[ActorRef] = None

  override def preStart() = {
    pspyChild = PowerSpyChild.props(sppUrl, version) match {
      case Some(props) => Some(context.actorOf(props, "pspy-child"))
      case _ => log.error("the PowerSpy ({}) is not reachable", sppUrl); None
    }

    pspyChild match {
      case Some(actorRef) => actorRef ! PSpyStart
      case _ => {}
    }
    super.preStart()
  }

  override def postStop() = {
    pspyChild match {
      case Some(actorRef) => gracefulStop(actorRef, timeout.duration)
      case _ => {}
    }

    super.postStop()
  }

  lazy val cpuTimesCache = new Cache[(Double, Double)]

  /**
   * The default behavior is overridden because this sensor handles other messages.
   */
  override def receive: Actor.Receive = running(List())

  def running(messages: List[PSpyChildMessage]): Actor.Receive = LoggingReceive {
    case msg: MonitorTick => sense(msg, messages)
    case msg: PSpyChildMessage => context.become(running(messages :+ msg))
  } orElse default

  def sense(monitorTick: MonitorTick, messages: List[PSpyChildMessage]): Unit = {
    PSpyChildMessage.avg(messages) match {
      case Some(avg) => {
        lazy val globalCpuTime = osHelper.getGlobalCpuTime match {
          case Some(time) => time.toDouble
          case _ => 1d // we cannot divide by 0
        }

        val processClaz = implicitly[ClassTag[Process]].runtimeClass
        val appClaz = implicitly[ClassTag[Application]].runtimeClass

        monitorTick.target match {
          case target if processClaz.isInstance(target) || appClaz.isInstance(target) => {
            lazy val targetCpuTime = osHelper.getTargetCpuTime(target) match {
              case Some(time) => time.toDouble
              case _ => 0d
            }

            val key = CacheKey(monitorTick.muid, monitorTick.target)
            val now = (targetCpuTime, globalCpuTime)
            val old = cpuTimesCache.getOrElse(key, now)
            val diffTimes = (now._1 - old._1, now._2 - old._2)

            val usage = diffTimes match {
              case diff: (Double, Double) if diff._1 > 0 && diff._2 > 0 && diff._1 < diff._2 => {
                cpuTimesCache.update(key, now)
                TargetUsageRatio(diff._1 / diff._2)
              }
              case _ => TargetUsageRatio(0.0)
            }

            publishPSpyDataReport(monitorTick.muid, monitorTick.target, usage, avg.rms, avg.uScale, avg.iScale, monitorTick.tick)(eventBus)
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

  /**
   * Not used here, the default behavior is overridden.
   */
  def sense(monitorTick: MonitorTick): Unit = {}
}
