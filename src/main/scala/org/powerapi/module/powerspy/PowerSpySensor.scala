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
import javax.microedition.io.{Connector, StreamConnection}
import akka.util.Timeout
import org.powerapi.core.{ActorComponent, MessageBus}

/**
 * PSpySensor configuration.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
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

/**
 * Internal messages.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
object PSpyStart
case class PSpyData(rms: Double, uScale: Float, iScale: Float)

/**
 * This class is responsible to observe the PowerSpy power meter and to forward the data
 * directly to the Sensor.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
class PowerSpyObserver(connection: StreamConnection, version: PowerSpyVersion, in: Reader, out: Writer)
  extends SimplePowerSpy(connection, version) with ActorComponent {

  import akka.event.LoggingReceive

  setInput(in)
  setOutput(out)

  override def fireDataUpdated(data: PowerSpyEvent): Unit = {
    if(data != null) {
      context.parent ! PSpyData(data.getCurrentRMS, data.getUScale, data.getIScale)
    }
  }

  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case PSpyStart => {
      startPowerMonitoring()
      context.become(running)
    }
  } orElse default

  def running: PartialFunction[Any, Unit] = LoggingReceive {
    case _ => log.debug("{}", "the connexion with PowerSpy is already established.")
  }

  override def postStop(): Unit = {
    stopPowerMonitoring()
    close()
    super.postStop()
  }
}

/**
 * Companion object for creating the observer actor.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
object PowerSpyObserver {
  import akka.actor.Props
  import java.io.{BufferedReader,InputStreamReader,PrintWriter}
  import javax.microedition.io.Connector

  def props(sppUrl: String, version: PowerSpyVersion): Option[Props] = {
    try {
      val connection = Connector.open(sppUrl).asInstanceOf[StreamConnection]
      val in = new BufferedReader(new InputStreamReader(connection.openInputStream()))
      val out = new PrintWriter(connection.openOutputStream())
      Some(Props(new PowerSpyObserver(connection, version, in, out)))
    }
    catch {
      case _: Throwable => None
    }
  }
}

/**
 * This actor does not handle messages.
 * This choice was made to keep the coherency for all component which can be attached to the API.
 * Its aim is to react to the data produced by the power meter and then to publish an OverallPower msg in the event bus.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
class PowerSpySensor(eventBus: MessageBus, timeout: Timeout) extends ActorComponent with Configuration {
  import akka.actor.ActorRef
  import akka.pattern.gracefulStop
  import org.powerapi.module.PowerUnit
  import org.powerapi.module.OverallPowerChannel.publishOverallPower

  var pSpyChild: Option[ActorRef] = None

  override def preStart() = {
    pSpyChild = PowerSpyObserver.props(sppUrl, version) match {
      case Some(props) => Some(context.actorOf(props, "pspy-child"))
      case _ => log.error("the PowerSpy ({}) is not reachable", sppUrl); None
    }

    pSpyChild match {
      case Some(actorRef) => actorRef ! PSpyStart
      case _ => {}
    }
    super.preStart()
  }

  override def postStop() = {
    pSpyChild match {
      case Some(actorRef) => gracefulStop(actorRef, timeout.duration)
      case _ => {}
    }

    super.postStop()
  }

  def receive: PartialFunction[Any, Unit] = {
    case msg: PSpyData => publishOverallPower(msg.rms * msg.uScale * msg.iScale, PowerUnit.W, "powerspy")(eventBus)
  }
}
