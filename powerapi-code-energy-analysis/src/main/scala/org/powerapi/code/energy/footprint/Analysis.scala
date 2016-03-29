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
package org.powerapi.code.energy.footprint

import scala.sys
import scala.sys.process.stringSeqToProcess
import org.powerapi.PowerMeter
import org.powerapi.core.Configuration
import org.powerapi.module.disk.simple.DiskSimpleModule
import org.powerapi.module.libpfm.LibpfmInterruptionCoreModule

object Analysis extends Configuration(None) with App {
  @volatile var powerMeters = Seq[PowerMeter]()
  @volatile var unixServers = Seq[UnixServerSocket]()

  val controlThread = new Thread {
    var running = true

    override def run(): Unit = {
      while(running) {
        Thread.sleep(5000)
      }
    }

    def cancel(): Unit = running = false
  }

  val shutdownHookThread = scala.sys.ShutdownHookThread {
    println("PowerAPI is shutting down ...")
    unixServers.foreach(_.cancel())
    powerMeters.foreach(_.shutdown())
    controlThread.cancel()
  }


  if (System.getProperty("os.name").toLowerCase.indexOf("nix") >= 0 || System.getProperty("os.name").toLowerCase.indexOf("nux") >= 0) Seq("bash", "scripts/system.bash").!

  val powerapi = PowerMeter.loadModule(LibpfmInterruptionCoreModule(), DiskSimpleModule(None))
  val server = new UnixServerSocket(powerapi)
  server.start()
  powerMeters +:= powerapi
  unixServers +:= server

  controlThread.start()
  controlThread.join()

  shutdownHookThread.join()
  shutdownHookThread.remove()
  sys.exit(0)
}
