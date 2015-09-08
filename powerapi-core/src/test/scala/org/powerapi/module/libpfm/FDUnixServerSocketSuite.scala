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

import java.io.{PrintWriter, File}
import java.nio.channels.Channels
import akka.actor.ActorSystem
import akka.testkit.TestKit
import jnr.unixsocket.{UnixSocketChannel, UnixSocketAddress}
import org.powerapi.UnitTest
import org.scalamock.scalatest.MockFactory

class FDUnixServerSocketSuite(system: ActorSystem) extends UnitTest(system) with MockFactory {
  def this() = this(ActorSystem("FDUnixServerSocketSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "A FDUnixServerSocket" should "create a UnixSocket, accept a client connexion, and process the data received" in {
    val ancillaryHelper = mock[AncillaryHelper]

    val controlFlowServerPath = "control-flow-server-test.sock"
    val fdFlowServerPath = "fd-flow-server-test.sock"
    val server = new FDUnixServerSocket(controlFlowServerPath, fdFlowServerPath, ancillaryHelper, testActor)
    server.start()

    val controlSocketAddr = new UnixSocketAddress(new File(controlFlowServerPath))
    val controlChannel = UnixSocketChannel.open(controlSocketAddr)
    val fdSocketAddr = new UnixSocketAddress(new File(fdFlowServerPath))
    val fdChannel = UnixSocketChannel.open(fdSocketAddr)

    ancillaryHelper.receiveFD _ expects * returning Some(0)
    ancillaryHelper.receiveFD _ expects * returning Some(1)
    ancillaryHelper.receiveFD _ expects * returning Some(2)
    ancillaryHelper.receiveFD _ expects * returning Some(3)

    val client1 = new Thread(new Runnable {
      def run() = {
        val writer = new PrintWriter(Channels.newOutputStream(controlChannel))
        writer.print("ID:=LABEL1\n")
        writer.flush()
        writer.print("NEVENTS:=2\n")
        writer.flush()
        writer.print("NCPUS:=2\n")
        writer.flush()
        writer.print("EVENT:=CPU_CLK_UNHALTED:THREAD_P\n")
        writer.flush()
        writer.write("CPU:=0\n")
        writer.flush()
        // fd sent
        writer.print("CPU:=1\n")
        writer.flush()
        // fd sent
        writer.print("EVENT:=CPU_CLK_UNHALTED:REF_P\n")
        writer.flush()
        writer.print("CPU:=0\n")
        writer.flush()
        // fd sent
        writer.print("CPU:=1\n")
        writer.flush()
        // fd sent
        writer.close()
      }
    })

    client1.start()

    val message = expectMsgClass(classOf[MethodInformation])
    message.methodId should equal("LABEL1")
    message.fds should equal(Map[String, Map[Int, Int]]("CPU_CLK_UNHALTED:THREAD_P" -> Map[Int, Int](0 -> 0, 1 -> 1), "CPU_CLK_UNHALTED:REF_P" -> Map[Int, Int](0 -> 2, 1 -> 3)))

    server.cancel()
    controlChannel.close()
    fdChannel.close()
  }

  it should "handle several connexion" in {
    val ancillaryHelper = mock[AncillaryHelper]

    val controlFlowServerPath = "control-flow-server-test.sock"
    val fdFlowServerPath = "fd-flow-server-test.sock"
    val server = new FDUnixServerSocket(controlFlowServerPath, fdFlowServerPath, ancillaryHelper, testActor)
    server.start()

    val controlSocketAddr = new UnixSocketAddress(new File(controlFlowServerPath))
    val controlChannel1 = UnixSocketChannel.open(controlSocketAddr)
    val controlChannel2 = UnixSocketChannel.open(controlSocketAddr)
    val fdSocketAddr = new UnixSocketAddress(new File(fdFlowServerPath))
    val fdChannel1 = UnixSocketChannel.open(fdSocketAddr)
    val fdChannel2 = UnixSocketChannel.open(fdSocketAddr)

    ancillaryHelper.receiveFD _ expects * anyNumberOfTimes() returning Some(0)

    val client1 = new Thread(new Runnable {
      def run() = {
        val writer = new PrintWriter(Channels.newOutputStream(controlChannel1))
        writer.print("ID:=LABEL1\n")
        writer.flush()
        writer.print("NEVENTS:=2\n")
        writer.flush()
        writer.print("NCPUS:=2\n")
        writer.flush()
        writer.print("EVENT:=CPU_CLK_UNHALTED:THREAD_P\n")
        writer.flush()
        writer.write("CPU:=0\n")
        writer.flush()
        // fd sent
        writer.print("CPU:=1\n")
        writer.flush()
        // fd sent
        writer.print("EVENT:=CPU_CLK_UNHALTED:REF_P\n")
        writer.flush()
        writer.print("CPU:=0\n")
        writer.flush()
        // fd sent
        writer.print("CPU:=1\n")
        writer.flush()
        // fd sent
        writer.close()
      }
    })

    val client2 = new Thread(new Runnable {
      def run() = {
        val writer = new PrintWriter(Channels.newOutputStream(controlChannel2))
        writer.print("ID:=LABEL2\n")
        writer.flush()
        writer.print("NEVENTS:=2\n")
        writer.flush()
        writer.print("NCPUS:=2\n")
        writer.flush()
        writer.print("EVENT:=CPU_CLK_UNHALTED:THREAD_P\n")
        writer.flush()
        writer.write("CPU:=0\n")
        writer.flush()
        // fd sent
        writer.print("CPU:=1\n")
        writer.flush()
        // fd sent
        writer.print("EVENT:=CPU_CLK_UNHALTED:REF_P\n")
        writer.flush()
        writer.print("CPU:=0\n")
        writer.flush()
        // fd sent
        writer.print("CPU:=1\n")
        writer.flush()
        // fd sent
        writer.close()
      }
    })

    client1.start()
    client2.start()

    for(message <- receiveN(2).asInstanceOf[Seq[MethodInformation]]) {
      message.methodId match {
        case "LABEL1"|"LABEL2" => {
          message.fds.keys should contain allOf("CPU_CLK_UNHALTED:THREAD_P", "CPU_CLK_UNHALTED:REF_P")
          message.fds("CPU_CLK_UNHALTED:THREAD_P").keys should contain allOf(0, 1)
          message.fds("CPU_CLK_UNHALTED:REF_P").keys should contain allOf(0, 1)
        }
        case _ => assert(false)
      }
    }

    server.cancel()
    controlChannel1.close()
    controlChannel2.close()
    fdChannel1.close()
    fdChannel2.close()
  }
}
