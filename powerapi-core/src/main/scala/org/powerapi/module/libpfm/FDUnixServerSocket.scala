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

import java.io.{IOException, BufferedReader, InputStreamReader, File}
import java.nio.channels.Channels
import akka.actor.ActorRef
import jnr.unixsocket.{UnixSocketChannel, UnixServerSocketChannel, UnixSocketAddress}
import org.apache.logging.log4j.LogManager

// Internal wrapper
case class MethodInformation(methodId: String, fds: Map[String, Map[Int, Int]])

/**
 * Allows to process a connexion with a client program.
 * The data are received according to a specific protocol. One channel is used to receive the headers, the second one
 * for receiving file descriptors.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class RequestHandler(flowChannel: UnixSocketChannel, fdChannel: UnixSocketChannel, ancillaryHelper: AncillaryHelper, recipient: ActorRef) extends Thread {
  private val log = LogManager.getLogger

  override def run(): Unit = {
    val request = scala.collection.mutable.HashMap[String, Map[Int, Int]]()
    val reader = new BufferedReader(new InputStreamReader(Channels.newInputStream(flowChannel)))
    var line = reader.readLine()
    var error = false
    var methodId = ""

    error = !line.startsWith("ID:=")
    if(!error) {
      methodId = line.replace("ID:=", "")
      log.debug("ID: {}", methodId)
      line = reader.readLine()
      error = !line.startsWith("NEVENTS:=")

      if(!error) {
        val nbEvents = line.replace("NEVENTS:=", "").toInt
        log.debug("NEVENTS: {}", s"$nbEvents")
        line = reader.readLine()
        error = !line.startsWith("NCPUS:=")

        if(!error) {
          val nbCPUs = line.replace("NCPUS:=", "").toInt
          log.debug("NCPUS: {}", s"$nbCPUs")

          for(_ <- 0 until nbEvents) {
            val fds = scala.collection.mutable.HashMap[Int, Int]()
            line = reader.readLine()
            error = !line.startsWith("EVENT:=")

            if(!error) {
              val event = line.replace("EVENT:=", "")
              log.debug("EVENT: {}", event)

              for(_ <- 0 until nbCPUs) {
                line = reader.readLine()
                error = !line.startsWith("CPU:=")

                if(!error) {
                  val cpu = line.replace("CPU:=", "").toInt
                  log.debug("CPU: {}", s"$cpu")

                  ancillaryHelper.receiveFD(fdChannel) match {
                    case Some(fd) => {
                      log.debug("fd: {}", s"$fd")
                      fds += cpu -> fd
                    }
                    case None => log.error("FD not correctly received")
                  }
                }
              }

              request += event -> fds.toMap
            }
          }
        }
      }
    }

    if(error) {
      log.error("Data received do not follow the right protocol")
    }

    else recipient ! MethodInformation(methodId, request.toMap)
  }
}

/**
 * Utility class to handle two UnixSocket server in order to exchange opened file descriptors from a client to PowerAPI.
 * The first UnixSocket server is only used to send the header (following a defined protocol), and the second one is
 * responsible for receiving file descriptors.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class FDUnixServerSocket(controlFlowServerPath: String, fdFlowServerPath: String, ancillaryHelper: AncillaryHelper, recipient: ActorRef) extends Thread {
  private val log = LogManager.getLogger

  val controlFlowSocketFile = new File(controlFlowServerPath)
  val fdFlowSocketFile = new File(fdFlowServerPath)

  val controlSocketAddr = new UnixSocketAddress(controlFlowSocketFile)
  val fdSocketAddr = new UnixSocketAddress(fdFlowSocketFile)

  val flowServerSocket = UnixServerSocketChannel.open()
  val fdServerSocket = UnixServerSocketChannel.open()

  flowServerSocket.socket.bind(controlSocketAddr)
  fdServerSocket.socket.bind(fdSocketAddr)

  @volatile var running = true

  override def run(): Unit = {
    while(running) {
      try {
        new RequestHandler(flowServerSocket.accept(), fdServerSocket.accept(), ancillaryHelper, recipient).start()
      }
      catch {
        case _: IOException => {}
        case ex: Throwable => log.error(ex)
      }
    }
  }

  def cancel(): Unit = {
    running = false
    flowServerSocket.close()
    fdServerSocket.close()
    controlFlowSocketFile.delete()
    fdFlowSocketFile.delete()
  }
}
