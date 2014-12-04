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
 * This actor does not handle messages.
 * This choice was made to keep the coherency for all component which can be attached to the API.
 * Its aim is to react to the data produced by the power meter and then to publish an OverallPower msg in the event bus.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
class PowerSpySensor(eventBus: MessageBus) extends ActorComponent with Configuration {
  import java.io.{BufferedReader,InputStreamReader,PrintWriter}
  import javax.microedition.io.{Connector, StreamConnection}
  import org.powerapi.module.OverallPowerChannel.publishOverallPower
  import org.powerapi.module.PowerUnit

  var pSpyMeter: Option[SimplePowerSpy] = {
    try {
      val connection = Connector.open(sppUrl).asInstanceOf[StreamConnection]
      val in = new BufferedReader(new InputStreamReader(connection.openInputStream()))
      val out = new PrintWriter(connection.openOutputStream())

      Some(
        new SimplePowerSpy(connection, version) {
          setInput(in)
          setOutput(out)

          override def fireDataUpdated(data: PowerSpyEvent): Unit = {
            if (data != null) {
              publishOverallPower(data.getCurrentRMS * data.getIScale * data.getUScale, PowerUnit.W, "powerspy")(eventBus)
            }
          }
        }
      )
    }
    catch {
      case _: Throwable => log.error("the PowerSpy ({}) is not reachable", sppUrl); None
    }
  }

  override def postStop(): Unit = {
    pSpyMeter match {
      case Some(pSpy) => {
        pSpy.stopPowerMonitoring()
        pSpy.close()
        log.debug("{}", "the connexion with the PowerSpy power meter is correctly closed")
      }
      case _ => log.debug("{}", "the PowerSpy power meter is not started")
    }
  }

  def receive: PartialFunction[Any, Unit] = {
    case _ => log.debug("{}", "the PowerSpy power meter cannot handled message")
  }
}
