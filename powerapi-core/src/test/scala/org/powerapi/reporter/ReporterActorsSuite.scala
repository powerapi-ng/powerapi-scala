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
package org.powerapi.reporter

import java.util.UUID

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import akka.actor.{Props, Terminated}
import akka.pattern.gracefulStop
import akka.testkit.{EventFilter, TestActorRef, TestProbe}
import akka.util.Timeout

import org.powerapi.core.power.{Power, _}
import org.powerapi.core.target.{All, Application, Process, Target}
import org.powerapi.core.{MessageBus, Tick}
import org.powerapi.module.PowerChannel.{AggregatePowerReport, RawPowerReport, render}
import org.powerapi.reporter.ReporterChannel.{ReporterStart, ReporterStop, ReporterStopAll, startReporter, stopAllReporter, stopReporter}
import org.powerapi.{PowerDisplay, UnitTest}

class ReporterActorsSuite extends UnitTest {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.shutdown()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A Reporter actor" should "launch an exception when the messages received cannot be handled" in new Bus {
    val muid1 = UUID.randomUUID()
    val muid2 = UUID.randomUUID()

    val output = new PowerDisplay {
      def display(muid: UUID, timestamp: Long, targets: Set[Target], devices: Set[String], power: Power): Unit = {}
    }

    val reporter1 = TestActorRef(Props(classOf[Reporter], eventBus, muid1, output), "reporter1")
    val reporter2 = TestActorRef(Props(classOf[Reporter], eventBus, muid2, output), "reporter2")

    EventFilter.warning(occurrences = 4, source = reporter1.path.toString).intercept({
      reporter1 ! ReporterStop("test", muid1)
      reporter1 ! ReporterStopAll("test")
      reporter1 ! ReporterStart("test", UUID.randomUUID(), output)
      reporter1 ! ReporterStart("test", muid1, new PowerDisplay {
        def display(muid: UUID, timestamp: Long, targets: Set[Target], devices: Set[String], power: Power): Unit = {}
      })
    })

    EventFilter.info(occurrences = 1, source = reporter1.path.toString).intercept({
      reporter1 ! ReporterStart("test", muid1, output)
    })

    EventFilter.info(occurrences = 1, source = reporter2.path.toString).intercept({
      reporter2 ! ReporterStart("test", muid2, output)
    })

    EventFilter.warning(occurrences = 2, source = reporter1.path.toString).intercept({
      reporter1 ! ReporterStart("test", muid1, output)
      reporter1 ! ReporterStop("test", UUID.randomUUID())
    })

    EventFilter.info(occurrences = 1, source = reporter1.path.toString).intercept({
      reporter1 ! ReporterStop("test", muid1)
    })

    EventFilter.info(occurrences = 1, source = reporter2.path.toString).intercept({
      reporter2 ! ReporterStopAll("test")
    })

    Await.result(gracefulStop(reporter1, timeout.duration), timeout.duration)
    Await.result(gracefulStop(reporter2, timeout.duration), timeout.duration)
  }

  it should "handle AggPowerReport messages" in new Bus {
    val muid1 = UUID.randomUUID()
    val muid2 = UUID.randomUUID()

    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }
    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 1000
    }

    val aggPowerReport1 = AggregatePowerReport(muid1)
    aggPowerReport1.aggregator = Some(MEDIAN)
    aggPowerReport1 += RawPowerReport("test", muid1, Application("firefox"), 10.W, "cpu", tick1)
    aggPowerReport1 += RawPowerReport("test", muid1, Process(1), 3.W, "gpu", tick1)
    aggPowerReport1 += RawPowerReport("test", muid1, Process(2), 5.W, "disk", tick1)

    val aggPowerReport2 = AggregatePowerReport(muid2)
    aggPowerReport2.aggregator = Some(MAX)
    aggPowerReport2 += RawPowerReport("test", muid2, All, 30.W, "cpu", tick2)

    val output = new PowerDisplay {
      def display(muid: UUID, timestamp: Long, targets: Set[Target], devices: Set[String], power: Power): Unit = testActor !(muid, timestamp, targets, devices, power)
    }

    val reporter1 = TestActorRef(Props(classOf[Reporter], eventBus, muid1, output), "reporter1")
    val reporter2 = TestActorRef(Props(classOf[Reporter], eventBus, muid2, output), "reporter2")
    val watcher = TestProbe()
    watcher.watch(reporter1)
    watcher.watch(reporter2)

    EventFilter.info(occurrences = 1, source = reporter1.path.toString).intercept({
      reporter1 ! ReporterStart("test", muid1, output)
    })

    EventFilter.info(occurrences = 1, source = reporter2.path.toString).intercept({
      reporter2 ! ReporterStart("test", muid2, output)
    })

    render(aggPowerReport1)(eventBus)
    var msg = expectMsgClass(classOf[(UUID, Long, Set[Target], Set[String], Power)])
    msg._1 should equal(muid1)
    msg._2 should equal(tick1.timestamp)
    msg._3 should contain theSameElementsAs Seq(Application("firefox"), Process(1), Process(2))
    msg._4 should contain theSameElementsAs Seq("cpu", "gpu", "disk")
    msg._5 should equal(MEDIAN(Seq(10.W, 3.W, 5.W)))

    render(aggPowerReport2)(eventBus)
    msg = expectMsgClass(classOf[(UUID, Long, Set[Target], Set[String], Power)])
    msg._1 should equal(muid2)
    msg._2 should equal(tick2.timestamp)
    msg._3 should contain theSameElementsAs Seq(All)
    msg._4 should contain theSameElementsAs Seq("cpu")
    msg._5 should equal(MAX(Seq(30.W)))

    EventFilter.info(occurrences = 1, source = reporter1.path.toString).intercept({
      reporter1 ! ReporterStop("test", muid1)
    })

    EventFilter.info(occurrences = 1, source = reporter2.path.toString).intercept({
      reporter2 ! ReporterStopAll("test")
    })

    render(aggPowerReport1)(eventBus)
    render(aggPowerReport2)(eventBus)
    expectNoMsg()

    watcher.receiveN(2).asInstanceOf[Seq[Terminated]].map(_.actor) should contain theSameElementsAs Seq(reporter1, reporter2)

    Await.result(gracefulStop(reporter1, timeout.duration), timeout.duration)
    Await.result(gracefulStop(reporter2, timeout.duration), timeout.duration)
  }

  "A Reporters actor" should "handle Reporter actors" in new Bus {
    val muid1 = UUID.randomUUID()
    val muid2 = UUID.randomUUID()
    val muid3 = UUID.randomUUID()

    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }
    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 1000
    }
    val aggPowerReport1 = AggregatePowerReport(muid1)
    aggPowerReport1.aggregator = Some(MEDIAN)
    aggPowerReport1 += RawPowerReport("test", muid1, Application("firefox"), 10.W, "cpu", tick1)
    aggPowerReport1 += RawPowerReport("test", muid1, Process(1), 3.W, "gpu", tick1)
    aggPowerReport1 += RawPowerReport("test", muid1, Process(2), 5.W, "disk", tick1)

    val aggPowerReport2 = AggregatePowerReport(muid2)
    aggPowerReport2.aggregator = Some(MAX)
    aggPowerReport2 += RawPowerReport("test", muid2, All, 30.W, "cpu", tick2)

    val output = new PowerDisplay {
      def display(muid: UUID, timestamp: Long, targets: Set[Target], devices: Set[String], power: Power): Unit = testActor !(muid, timestamp, targets, devices, power)
    }

    val reporters = TestActorRef(Props(classOf[Reporters], eventBus), "reporters")
    val watcher = TestProbe()

    EventFilter.info(occurrences = 3, start = s"reporter is started, class: ${output.getClass.getName}").intercept({
      startReporter(muid1, output)(eventBus)
      startReporter(muid2, output)(eventBus)
      startReporter(muid3, output)(eventBus)
    })

    EventFilter.warning(occurrences = 1, start = s"this reporter is started already, class: ${output.getClass.getName}").intercept({
      startReporter(muid1, output)(eventBus)
    })

    val reporterChildren = reporters.children.seq
    reporterChildren foreach watcher.watch

    render(aggPowerReport1)(eventBus)
    var msg = expectMsgClass(classOf[(UUID, Long, Set[Target], Set[String], Power)])
    msg._1 should equal(muid1)
    msg._2 should equal(tick1.timestamp)
    msg._3 should contain theSameElementsAs Seq(Application("firefox"), Process(1), Process(2))
    msg._4 should contain theSameElementsAs Seq("cpu", "gpu", "disk")
    msg._5 should equal(MEDIAN(Seq(10.W, 3.W, 5.W)))

    render(aggPowerReport2)(eventBus)
    msg = expectMsgClass(classOf[(UUID, Long, Set[Target], Set[String], Power)])
    msg._1 should equal(muid2)
    msg._2 should equal(tick2.timestamp)
    msg._3 should contain theSameElementsAs Seq(All)
    msg._4 should contain theSameElementsAs Seq("cpu")
    msg._5 should equal(MAX(Seq(30.W)))

    EventFilter.info(occurrences = 3, start = s"reporter is stopped, class: ${output.getClass.getName}").intercept({
      stopReporter(muid1)(eventBus)
      stopAllReporter(eventBus)
    })

    render(aggPowerReport1)(eventBus)
    render(aggPowerReport2)(eventBus)
    expectNoMsg()

    watcher.receiveN(3).asInstanceOf[Seq[Terminated]].map(_.actor) should contain theSameElementsAs reporterChildren

    Await.result(gracefulStop(reporters, timeout.duration), timeout.duration)
  }
}
