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
import java.util.UUID
import akka.actor.{Props, ActorSystem}
import akka.pattern.gracefulStop
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import jnr.unixsocket.{UnixSocketChannel, UnixSocketAddress}
import org.powerapi.UnitTest
import org.powerapi.core.ClockChannel.ClockTick
import org.powerapi.core.MessageBus
import org.powerapi.core.MonitorChannel.MonitorTick
import org.powerapi.core.target.{Code, Process}
import org.powerapi.module.libpfm.PerformanceCounterChannel.{PCReport, subscribePCReport}
import org.scalamock.scalatest.MockFactory
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class LibpfmCoreCodeSensorSuite(system: ActorSystem) extends UnitTest(system) with MockFactory {

  def this() = this(ActorSystem("LibpfmCoreCodeSensorSuite"))

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  val topology = Map(0 -> Set(0), 1 -> Set(1))
  val events = Set("event", "event1")

  "A LibpfmCoreCodeSensor" should "process ticks and methods information sent by client" in new Bus {
    val libpfmHelper = mock[LibpfmHelper]
    val ancillaryHelper = mock[AncillaryHelper]

    val muid1 = UUID.randomUUID()
    val controlFlowServerPath = "control-flow-server-test.sock"
    val fdFlowServerPath = "fd-flow-server-test.sock"

    val sensor = TestActorRef(Props(classOf[LibpfmCoreCodeSensor], eventBus, libpfmHelper, Timeout(1.seconds), topology, events, controlFlowServerPath, fdFlowServerPath, ancillaryHelper), "core-code-sensor1")(system)
    subscribePCReport(eventBus)(testActor)

    sensor ! MonitorTick("monitor", muid1, Process(1), ClockTick("clock", 1.second))
    expectMsgClass(classOf[PCReport]) match {
      case PCReport(_, _, target, wrappers, _) => {
        target should equal(Process(1))
        wrappers.isEmpty should equal(true)
      }
    }
    sensor ! MonitorTick("monitor", muid1, Code("label1"), ClockTick("clock", 1.second))
    expectMsgClass(classOf[PCReport]) match {
      case PCReport(_, _, target, wrappers, _) => {
        target should equal(Code("label1"))
        wrappers.isEmpty should equal(true)
      }
    }

    ancillaryHelper.receiveFD _ expects * returning Some(0)
    ancillaryHelper.receiveFD _ expects * returning Some(2)
    ancillaryHelper.receiveFD _ expects * returning Some(1)
    ancillaryHelper.receiveFD _ expects * returning Some(3)

    val controlSocketAddr = new UnixSocketAddress(new File(controlFlowServerPath))
    val controlChannel = UnixSocketChannel.open(controlSocketAddr)
    val fdSocketAddr = new UnixSocketAddress(new File(fdFlowServerPath))
    val fdChannel = UnixSocketChannel.open(fdSocketAddr)
    val writer = new PrintWriter(Channels.newOutputStream(controlChannel))
    writer.print("ID:=label1\n")
    writer.flush()
    writer.print("NEVENTS:=2\n")
    writer.flush()
    writer.print("NCPUS:=2\n")
    writer.flush()
    writer.print("EVENT:=event\n")
    writer.flush()
    writer.write("CPU:=0\n")
    writer.flush()
    // fd sent
    writer.print("CPU:=1\n")
    writer.flush()
    // fd sent
    writer.print("EVENT:=event1\n")
    writer.flush()
    writer.print("CPU:=0\n")
    writer.flush()
    // fd sent
    writer.print("CPU:=1\n")
    writer.flush()
    // fd sent
    awaitAssert({
      sensor.underlyingActor.asInstanceOf[LibpfmCoreCodeSensor].methods.toSeq.apply(0) should equal(MethodInformation("label1", Map[String, Map[Int, Int]]("event" -> Map[Int, Int](0 -> 0, 1 -> 2), "event1" -> Map[Int, Int](0 -> 1, 1 -> 3))))
    }, 10.seconds, 1.seconds)
    libpfmHelper.readPC _ expects * repeat 4 returning Array(1, 1, 1)
    sensor ! MonitorTick("monitor", muid1, Code("label1"), ClockTick("clock", 1.second))
    expectMsgClass(classOf[PCReport]) match {
      case PCReport(_, _, target, wrappers, _) => {
        target should equal(Code("label1"))
        wrappers.size should equal(topology.size * events.size)
        events.foreach(event => wrappers.count(_.event == event) should equal(topology.size))
        wrappers.foreach(wrapper => wrapper.values.size should equal(topology(0).size))

        for (wrapper <- wrappers) {
          Future.sequence(wrapper.values) onSuccess {
            case coreValues: List[Long] => {
              val aggValue = coreValues.foldLeft(0l)((acc, value) => acc + value)
              aggValue should equal(1l)
            }
          }
        }
      }
    }
    libpfmHelper.readPC _ expects 0 returning Array(5, 2, 2)
    libpfmHelper.readPC _ expects 1 returning Array(6, 2, 2)
    libpfmHelper.readPC _ expects 2 returning Array(7, 2, 2)
    libpfmHelper.readPC _ expects 3 returning Array(8, 2, 2)
    libpfmHelper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(5l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(4)
    libpfmHelper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(6l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(5)
    libpfmHelper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(7l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(6)
    libpfmHelper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(8l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(7)
    val results = Map[(Int, String), Long]((0, "event") -> 4, (0, "event1") -> 5, (1, "event") -> 6, (1, "event1") -> 7)
    sensor ! MonitorTick("monitor", muid1, Code("label1"), ClockTick("clock", 1.second))
    expectMsgClass(classOf[PCReport]) match {
      case PCReport(_, _, target, wrappers, _) => {
        target should equal(Code("label1"))
        wrappers.size should equal(topology.size * events.size)
        events.foreach(event => wrappers.count(_.event == event) should equal(topology.size))
        wrappers.foreach(wrapper => wrapper.values.size should equal(topology(0).size))

        for ((core, _) <- topology) {
          for (event <- events) {
            Future.sequence(wrappers.filter(wrapper => wrapper.core == core && wrapper.event == event).head.values) onSuccess {
              case values: List[Long] => values.foldLeft(0l)((acc, value) => acc + value) should equal(results(core, event))
            }
          }
        }
      }
    }

    writer.close()
    controlChannel.close()
    fdChannel.close()
    Await.result(gracefulStop(sensor, timeout.duration), timeout.duration)
  }
}
