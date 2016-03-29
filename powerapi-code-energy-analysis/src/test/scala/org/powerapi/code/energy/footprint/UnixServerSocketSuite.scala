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

import java.io.{BufferedWriter, File, OutputStreamWriter}
import java.nio.channels.Channels
import java.util.UUID

import akka.actor.Props
import akka.pattern.gracefulStop
import akka.testkit.{EventFilter, TestActorRef, TestProbe}
import akka.util.Timeout
import com.paulgoldbaum.influxdbclient.InfluxDB
import jnr.unixsocket.{UnixServerSocketChannel, UnixSocketAddress, UnixSocketChannel}
import org.joda.time.format.ISODateTimeFormat
import org.powerapi.core.MonitorChannel.{MonitorStart, MonitorTick, subscribeMonitorTick}
import org.powerapi.core.target.{Application, Process}
import org.powerapi.core.power._
import org.powerapi.core.{MessageBus, MonitorChild}
import org.powerapi.module.PowerChannel.{AggregatePowerReport, RawPowerReport}
import org.powerapi.module.libpfm.PCInterruptionChannel.InterruptionTick
import org.powerapi.module.libpfm.PayloadProtocol.Payload
import org.powerapi.module.libpfm.{AgentTick, PayloadProtocol, TID}
import org.powerapi.{PowerDisplay, PowerMeter, PowerMonitoring, UnitTest}
import org.scalamock.scalatest.MockFactory

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class UnixServerSocketSuite extends UnitTest with MockFactory {

  val timeout = Timeout(1.seconds)
  val baseTopology = Map(0 -> Set(0), 1 -> Set(1))

  val influxHost = "localhost"
  val influxPort = 8086
  val influxUser = "powerapi"
  val influxPwd = "powerapi"
  val influxDB = "codenergy"

  val influxdb = InfluxDB.connect(influxHost, influxPort, influxUser, influxPwd)
  val database = influxdb.selectDatabase(influxDB)
  Await.result(database.create(), timeout.duration)

  override def afterAll() = {
    Await.result(database.drop(), timeout.duration)
    system.shutdown()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "An UnixServerSocket" should "be able to handle connexions with PowerAPI agents" in new Bus {
    class DataAgentSocket(dataSocket: UnixSocketChannel, payload: Payload) extends Thread {
      val stream = Channels.newOutputStream(dataSocket)

      def htonl(x: Int): Array[Byte] = {
        var _x = x
        val res: Array[Byte] = Array.fill(4){0}

        for (i <- 0 until 4) {
          res(i) = new Integer(_x >>> 24).byteValue()
          _x <<= 8
        }
        res
      }

      override def run(): Unit = {
        val size = payload.getSerializedSize
        stream.write(htonl(size))
        stream.flush()
        payload.writeTo(stream)
        stream.flush()
        stream.write(htonl(size))
        stream.flush()
        payload.writeTo(stream)
        stream.flush()
      }
    }

    class Agent(muid: UUID, software: String, payloads: Map[Int, Payload]) extends Thread {
      val probe = TestProbe()
      subscribeMonitorTick(muid, software)(eventBus)(probe.ref)

      override def run(): Unit = {
        val controlPath = new File("/tmp/agent-control.sock")
        val controlAddress = new UnixSocketAddress(controlPath)
        val controlWriter = new BufferedWriter(new OutputStreamWriter(Channels.newOutputStream(UnixSocketChannel.open(controlAddress))))

        val servers = (for (core <- baseTopology.values.flatten) yield {
          val dataPath = new File(s"/tmp/agent-$core-$software.sock")
          val dataAddress = new UnixSocketAddress(dataPath)
          val dataServer = UnixServerSocketChannel.open()
          dataServer.socket().bind(dataAddress)
          core -> (dataPath, dataServer)
        }).toMap

        controlWriter.write(s"$software\n")
        controlWriter.flush()

        val dataAgents = for((core, (dataPath, dataServer)) <- servers) yield {
          val dataAgent = new DataAgentSocket(dataServer.accept(), payloads(core))
          dataAgent.start()
          dataAgent
        }

        probe.receiveN(2).asInstanceOf[Seq[MonitorTick]].foreach {
          monitorTick: MonitorTick =>
            val tick = monitorTick.tick.asInstanceOf[AgentTick]
            tick.payload should equal(payloads(tick.payload.getCore))
        }

        controlWriter.write("end\n")
        controlWriter.flush()

        dataAgents.foreach(_.join)

        servers.values.foreach {
          case (dataPath, dataServer) =>
            dataServer.close()
            dataPath.delete()
        }
      }
    }

    class PowerMeterMock extends PowerMeter(system, Seq())

    val muid1 = UUID.randomUUID()
    val app1 = Application("test1")
    val payloads1: Map[Int, Payload] = Map(
      0 -> Payload.newBuilder()
        .setCore(0)
        .setPid(10)
        .setTid(10)
        .setTimestamp(System.currentTimeMillis())
        .addCounters(PayloadProtocol.MapEntry.newBuilder().setKey("event1").setValue(200))
        .addCounters(PayloadProtocol.MapEntry.newBuilder().setKey("event2").setValue(400))
        .addTraces("a")
        .addTraces("main")
        .build(),
      1 -> Payload.newBuilder()
        .setCore(1)
        .setPid(10)
        .setTid(11)
        .setTimestamp(System.currentTimeMillis())
        .addCounters(PayloadProtocol.MapEntry.newBuilder().setKey("event1").setValue(200))
        .addCounters(PayloadProtocol.MapEntry.newBuilder().setKey("event2").setValue(400))
        .addTraces("b")
        .addTraces("a")
        .addTraces("main")
        .build()
    )
    val muid2 = UUID.randomUUID()
    val app2 = Application("test2")
    val payloads2: Map[Int, Payload] = Map(
      0 -> Payload.newBuilder()
        .setCore(0)
        .setPid(20)
        .setTid(20)
        .setTimestamp(System.currentTimeMillis())
        .addCounters(PayloadProtocol.MapEntry.newBuilder().setKey("event1").setValue(200000))
        .addCounters(PayloadProtocol.MapEntry.newBuilder().setKey("event2").setValue(300000))
        .addTraces("z")
        .addTraces("main")
        .build(),
      1 -> Payload.newBuilder()
        .setCore(1)
        .setPid(20)
        .setTid(21)
        .setTimestamp(System.currentTimeMillis())
        .addCounters(PayloadProtocol.MapEntry.newBuilder().setKey("event1").setValue(125000000))
        .addCounters(PayloadProtocol.MapEntry.newBuilder().setKey("event2").setValue(454444555))
        .addTraces("z")
        .addTraces("z")
        .addTraces("z")
        .addTraces("main")
        .build()
    )

    val monitorActor1 = TestActorRef(Props(classOf[MonitorChild], eventBus, muid1, Set(app1)), "monitor1")
    val monitorActor2 = TestActorRef(Props(classOf[MonitorChild], eventBus, muid2, Set(app2)), "monitor2")

    EventFilter.info(occurrences = 1, source = monitorActor1.path.toString).intercept({
      monitorActor1 ! MonitorStart("test", muid1, Set(app1))
    })

    EventFilter.info(occurrences = 1, source = monitorActor2.path.toString).intercept({
      monitorActor2 ! MonitorStart("test", muid2, Set(app2))
    })

    val pMeter = mock[PowerMeterMock]

    val monitor1 = mock[PowerMonitoring]
    monitor1.apply _ expects * once() returning monitor1
    (monitor1.to(_: PowerDisplay)) expects * anyNumberOfTimes() returning monitor1
    monitor1.cancel _ expects () anyNumberOfTimes()
    monitor1.eventBus _ expects () anyNumberOfTimes() returning eventBus
    monitor1.muid _ expects () anyNumberOfTimes() returning muid1
    pMeter.monitor _ expects Seq(app1) returning monitor1

    val monitor2 = mock[PowerMonitoring]
    monitor2.apply _ expects * once() returning monitor2
    (monitor2.to(_: PowerDisplay)) expects * anyNumberOfTimes() returning monitor2
    monitor2.cancel _ expects () anyNumberOfTimes()
    monitor2.eventBus _ expects () anyNumberOfTimes() returning eventBus
    monitor2.muid _ expects () anyNumberOfTimes() returning muid2
    pMeter.monitor _ expects Seq(app2) returning monitor2

    val baseConfiguration = new UnixServerSocketConfiguration {
      override lazy val topology = baseTopology
      override lazy val influxHost = "localhost"
      override lazy val influxPort = 8086
      override lazy val influxUser = "powerapi"
      override lazy val influxPwd = "powerapi"
      override lazy val influxDB = "codenergy"
    }

    val server = new UnixServerSocket(pMeter) {
      override val configuration = baseConfiguration
    }
    server.start()

    val agent1 = new Agent(muid1, "test1", payloads1)
    val agent2 = new Agent(muid2, "test2", payloads2)
    agent1.start()
    agent2.start()

    agent1.join()
    agent2.join()

    server.cancel()

    Await.result(gracefulStop(monitorActor1, timeout.duration), timeout.duration)
    Await.result(gracefulStop(monitorActor2, timeout.duration), timeout.duration)
  }

  "An InterruptionInfluxDisplay" should "write data inside an Influx database" in {
    val timeout = 10.seconds

    val muid1 = UUID.randomUUID()
    val muid2 = UUID.randomUUID()

    val timestamp1 = System.nanoTime()
    val report1 = new AggregatePowerReport(muid1) {
      override def rawPowers = Seq(
        RawPowerReport("", muid1, Application("test"), 10.W, "cpu", InterruptionTick("", 0, TID(10), "a.b.b", timestamp1, true)),
        RawPowerReport("", muid1, Application("test"), 2.W, "disk", AgentTick("", timestamp1, Payload.newBuilder().setCore(0).setPid(10).setTid(10).setTimestamp(timestamp1).build()))
      )
    }

    val timestamp2 = System.nanoTime() + 1.seconds.toNanos
    val report2 = new AggregatePowerReport(muid1) {
      override def rawPowers = Seq(
        RawPowerReport("", muid1, Application("test"), 10.W, "cpu", InterruptionTick("", 0, TID(10), "a.b.b", timestamp2, false)),
        RawPowerReport("", muid1, Application("test"), 1.W, "cpu", InterruptionTick("", 1, TID(11), "w.x.y", timestamp2, true)),
        RawPowerReport("", muid1, Application("test"), 8.W, "disk", AgentTick("", timestamp1, Payload.newBuilder().setCore(1).setPid(11).setTid(11).setTimestamp(timestamp2).build()))
      )
    }

    val timestamp3 = System.nanoTime() + 2.seconds.toNanos
    val report3 = new AggregatePowerReport(muid2) {
      override def rawPowers = Seq(
        RawPowerReport("", muid2, Application("test"), 1.W, "cpu", InterruptionTick("", 0, TID(12), "w.x.y", timestamp3, true)),
        RawPowerReport("", muid2, Application("test"), 1.W, "disk", AgentTick("", timestamp3, Payload.newBuilder().setCore(0).setPid(12).setTid(12).setTimestamp(timestamp3).build()))
      )
    }

    val display1 = new InterruptionInfluxDisplay(influxHost, influxPort, influxUser, influxPwd, influxDB, "test")

    display1.display(report1)
    awaitCond({
      val result = Await.result(database.query("select * from test order by time"), timeout)
      display1.run == 1 &&
      result.series.size == 1 &&
      result.series.head.records.size == 1  &&
      result.series.head.records.last("cpu").toString.toLong == 10 &&
      result.series.head.records.last("disk").toString.toLong == 2 &&
      result.series.head.records.last("core").toString == "0" &&
      result.series.head.records.last("method").toString == "a.b.b" &&
      result.series.head.records.last("run").toString == "1" &&
      result.series.head.records.last("tid").toString == "10"
    }, 30.seconds, 1.seconds)

    display1.display(report2)
    awaitCond({
      val result = Await.result(database.query("select * from test order by time"), timeout)

      if (result.series.size == 1) {
        val filteredResult = result.series.head.records.filter(record => ISODateTimeFormat.dateTimeParser().parseDateTime(record.apply("time").toString).getMillis == (timestamp2 / 1e6).toLong)
        display1.run == 1 &&
        result.series.head.records.size == 3 &&
        filteredResult.size == 2 &&
        filteredResult.exists {
          record =>
            record.apply("cpu").toString.toLong == 10 &&
            record.apply("disk").toString.toLong == 0 &&
            record.apply("method").toString == "a.b.b" &&
            record.apply("run").toString == "1" &&
            record.apply("tid").toString == "10"
            record.apply("core").toString == "0"
        } &&
        filteredResult.exists {
          record =>
            record.apply("cpu").toString.toLong == 1 &&
            record.apply("disk").toString.toLong == 8 &&
            record.apply("method").toString == "w.x.y" &&
            record.apply("run").toString == "1" &&
            record.apply("tid").toString == "11"
            record.apply("core").toString == "1"
        }
      }
      else false
    }, 30.seconds, 1.seconds)

    val display2 = new InterruptionInfluxDisplay(influxHost, influxPort, influxUser, influxPwd, influxDB, "test")

    display2.display(report3)
    awaitCond({
      val result = Await.result(database.query("select * from test order by time"), timeout)

      if (result.series.size == 1) {
        val filteredResult = result.series.head.records.filter(record => ISODateTimeFormat.dateTimeParser().parseDateTime(record.apply("time").toString).getMillis == (timestamp3 / 1e6).toLong)
        display2.run == 2 &&
          result.series.head.records.size == 4 &&
          filteredResult.size == 1 &&
          filteredResult.exists {
            record =>
              record.apply("cpu").toString.toLong == 1 &&
              record.apply("disk").toString.toLong == 1 &&
              record.apply("method").toString == "w.x.y" &&
              record.apply("run").toString == "2" &&
              record.apply("tid").toString == "12"
              record.apply("core").toString == "0"
          }
      }
      else false
    }, 30.seconds, 1.seconds)
  }
}
