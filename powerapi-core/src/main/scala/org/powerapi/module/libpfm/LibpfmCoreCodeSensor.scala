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

import akka.actor.Props
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import org.powerapi.core.target.Code
import org.powerapi.core.{APIComponent, MessageBus}
import org.powerapi.core.MonitorChannel.{MonitorTick, subscribeMonitorTick}
import org.powerapi.module.SensorChannel.{MonitorStopAll, MonitorStop, subscribeSensorsChannel}
import org.powerapi.module.libpfm.PerformanceCounterChannel.{formatLibpfmPickerNameForCoreMethod, PCWrapper, publishPCReport}
import scala.concurrent.Future

/**
 * Sensor for getting the performance counter values per method (represented as a label).
 * It handles a specific unix domain socket for opening a connexion with a software and for exchanging information
 * (e.g., file descriptors).
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class LibpfmCoreCodeSensor(eventBus: MessageBus, libpfmHelper: LibpfmHelper, timeout: Timeout, topology: Map[Int, Set[Int]], events: Set[String], controlFlowServerPath: String, fdFlowServerPath: String, ancillaryHelper: AncillaryHelper) extends APIComponent {
  var server: Option[FDUnixServerSocket] = None

  val methods = scala.collection.mutable.Set[MethodInformation]()
  val wrappers = scala.collection.mutable.Map[(Int, String), PCWrapper]()

  override def preStart(): Unit = {
    subscribeMonitorTick(eventBus)(self)
    subscribeSensorsChannel(eventBus)(self)

    // Starts the UnixSocket server
    server = Some(new FDUnixServerSocket(controlFlowServerPath, fdFlowServerPath, ancillaryHelper, self))
    server.get.start()

    super.preStart()
  }

  override def postStop(): Unit = {
    server match {
      case Some(s) => s.cancel()
      case None => {}
    }
  }

  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case msg: MethodInformation => methods += msg
    case msg: MonitorTick => sense(msg)
    case msg: MonitorStop => monitorStopped(msg)
    case msg: MonitorStopAll => monitorAllStopped(msg)
  } orElse default

  def sense(monitorTick: MonitorTick): Unit = {
    monitorTick.target match {
      case code: Code => {
        val targetedFds = methods.filter(_.methodId == code.label)

        if(targetedFds.nonEmpty) {
          val method = targetedFds.toSeq.apply(0)

          for((core, indexes) <- topology) {
            for(index <- indexes) {
              for(event <- events) {
                if(method.fds.contains(event)) {
                  val name = formatLibpfmPickerNameForCoreMethod(index, event, monitorTick.muid, method.methodId)

                  val actor = context.child(name) match {
                    case Some(ref) => ref
                    case None => context.actorOf(Props(classOf[FDLibpfmPicker], libpfmHelper,
                      Some(method.fds(event).filter(_._1 == index).toSeq.apply(0)._2)), name)
                  }

                  wrappers += (core, event) -> (wrappers.getOrElse((core, event), PCWrapper(core, event, List())) + actor.?(monitorTick)(timeout).asInstanceOf[Future[Long]])
                }
              }
            }
          }
        }
      }
      case _ => log.warning("Only Code targets can be used with this Sensor")
    }

    publishPCReport(monitorTick.muid, monitorTick.target, wrappers.values.toList, monitorTick.tick)(eventBus)
    wrappers.clear()
  }

  def monitorStopped(msg: MonitorStop): Unit = {
    context.actorSelection(s"*${msg.muid}*") ! msg
    wrappers.clear()
  }

  def monitorAllStopped(msg: MonitorStopAll): Unit = {
    context.actorSelection("*") ! msg
    wrappers.clear()
    methods.clear()
  }
}
