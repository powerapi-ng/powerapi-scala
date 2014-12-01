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

import java.io.{Reader, Writer}
import javax.microedition.io.StreamConnection
import akka.event.LoggingReceive
import org.powerapi.core.ActorComponent

/**
 * This child is responsible to react when the PowerSpy power meter produce messages.
 */
class PowerSpyChild(connection: StreamConnection, version: PowerSpyVersion, in: Reader, out: Writer)
  extends SimplePowerSpy(connection, version) with ActorComponent {

  import org.powerapi.module.powerspy.PSpyMetricsChannel.{PSpyChildMessage, PSpyStart}

  setInput(in)
  setOutput(out)

  override def fireDataUpdated(data: PowerSpyEvent): Unit = {
    if(data != null) {
      context.parent ! PSpyChildMessage(data.getCurrentRMS, data.getUScale, data.getIScale)
    }
  }

  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case PSpyStart => startPowerMonitoring()
  } orElse default

  override def postStop(): Unit = {
    stopPowerMonitoring()
    close()
    super.postStop()
  }
}

/**
 * Companion object for creating the underlying actor.
 */
object PowerSpyChild {
  import akka.actor.Props
  import java.io.{BufferedReader,InputStreamReader,PrintWriter}
  import javax.microedition.io.Connector

  def props(sppUrl: String, version: PowerSpyVersion): Option[Props] = {
    try {
      val connection = Connector.open(sppUrl).asInstanceOf[StreamConnection]
      val in = new BufferedReader(new InputStreamReader(connection.openInputStream()))
      val out = new PrintWriter(connection.openOutputStream())
      Some(Props(new PowerSpyChild(connection, version, in, out)))
    }
    catch {
      case _: Throwable => None
    }
  }
}
