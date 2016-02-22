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
package org.powerapi.core

import java.util.UUID

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import akka.actor.{Actor, ActorRef, Props, Terminated}
import akka.event.Logging.Info
import akka.pattern.gracefulStop
import akka.testkit.{EventFilter, TestActorRef, TestProbe}
import akka.util.Timeout

import org.powerapi.core.MonitorChannel._
import org.powerapi.core.power._
import org.powerapi.core.target.{All, Application, Process, Target, intToProcess, stringToApplication}
import org.powerapi.module.FormulaChannel.startFormula
import org.powerapi.module.PowerChannel.{AggregatePowerReport, RawPowerReport, publishRawPowerReport, subscribeAggPowerReport, unsubscribeAggPowerReport}
import org.powerapi.module.SensorChannel.startSensor
import org.powerapi.module.{Formula, Formulas, Sensor, Sensors}
import org.powerapi.reporter.{Reporters}
import org.powerapi.{PowerDisplay, UnitTest}

class EmptySensor(eventBus: MessageBus, muid: UUID, target: Target) extends Sensor(eventBus, muid, target) {
  def init(): Unit = {}

  def terminate(): Unit = {}

  def handler: Actor.Receive = sensorDefault
}

class EmptyFormula(eventBus: MessageBus, muid: UUID, target: Target) extends Formula(eventBus, muid, target) {
  def init(): Unit = {}

  def terminate(): Unit = {}

  def handler: Actor.Receive = formulaDefault
}

class MonitorSuite extends UnitTest {

  val timeout = Timeout(1.seconds)
  val threshold = 0.5

  override def afterAll() = {
    system.shutdown()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A MonitorChild actor" should "launch an exception when the messages received cannot be handled" in new Bus {
    val muid = UUID.randomUUID()
    val frequency = 50.milliseconds
    val targets = Set[Target](1)

    val monitor = TestActorRef(Props(classOf[MonitorChild], eventBus, muid, targets), "monitor")

    EventFilter.warning(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStop("test", muid)
    })

    // Not an exception, just an assessment (switching in the running state).
    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStart("test", muid, targets)
    })

    EventFilter.warning(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStart("test", muid, targets)
    })

    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorFrequency("test", muid, 1.second)
    })

    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorAggregator("test", muid, MAX)
    })

    EventFilter.warning(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorFrequency("test", UUID.randomUUID(), 1.second)
    })

    EventFilter.warning(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorAggregator("test", UUID.randomUUID(), MAX)
    })

    EventFilter.warning(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStop("test", UUID.randomUUID())
    })

    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStop("test", muid)
    })

    Await.result(gracefulStop(monitor, timeout.duration), timeout.duration)
  }

  it should "produce MonitorTick per muid/target when a Tick is received" in new Bus {
    val muid1 = UUID.randomUUID()
    val muid2 = UUID.randomUUID()
    val targets = Set[Target](1, "java", All)

    val monitor1 = TestActorRef(Props(classOf[MonitorChild], eventBus, muid1, targets), "monitor1")
    val monitor2 = TestActorRef(Props(classOf[MonitorChild], eventBus, muid2, targets), "monitor2")
    val watcher = TestProbe()
    watcher.watch(monitor1)
    watcher.watch(monitor2)

    targets.foreach(target => {
      subscribeMonitorTick(muid1, target)(eventBus)(testActor)
      subscribeMonitorTick(muid2, target)(eventBus)(testActor)
    })

    EventFilter.info(occurrences = 1, source = monitor1.path.toString).intercept({
      monitor1 ! MonitorStart("test", muid1, targets)
    })

    EventFilter.info(occurrences = 1, source = monitor2.path.toString).intercept({
      monitor2 ! MonitorStart("test", muid2, targets)
    })

    monitor1 ! new Tick {
      val topic = ""
      val timestamp = System.currentTimeMillis()
    }
    receiveN(targets.size).asInstanceOf[Seq[MonitorTick]].map(_.target) should contain theSameElementsAs targets
    monitor2 ! new Tick {
      val topic = ""
      val timestamp = System.currentTimeMillis()
    }
    receiveN(targets.size).asInstanceOf[Seq[MonitorTick]].map(_.target) should contain theSameElementsAs targets

    EventFilter.info(occurrences = 1, source = monitor1.path.toString).intercept({
      monitor1 ! MonitorStop("test", muid1)
    })

    EventFilter.info(occurrences = 1, source = monitor2.path.toString).intercept({
      monitor2 ! MonitorStop("test", muid2)
    })

    watcher.receiveN(2).asInstanceOf[Seq[Terminated]].map(_.actor) should contain theSameElementsAs Seq(monitor1, monitor2)

    Await.result(gracefulStop(monitor1, timeout.duration), timeout.duration)
    Await.result(gracefulStop(monitor2, timeout.duration), timeout.duration)
  }

  it should "handle a clock at a given frequency when needed, and produce MonitorTick per muid/target when a Tick is received" in new Bus {
    val clocks = TestActorRef(Props(classOf[Clocks], eventBus), "clocks")

    val frequency1 = 250.milliseconds
    val frequency2 = 50.milliseconds
    val frequency3 = 1.second

    val muid1 = UUID.randomUUID()
    val muid2 = UUID.randomUUID()
    val targets = Set[Target](1, "java", All)

    var messages = Seq[MonitorTick]()

    val monitor1 = TestActorRef(Props(classOf[MonitorChild], eventBus, muid1, targets), "monitor1")
    val monitor2 = TestActorRef(Props(classOf[MonitorChild], eventBus, muid2, targets), "monitor2")
    val watcher = TestProbe()
    watcher.watch(monitor1)
    watcher.watch(monitor2)

    targets.foreach(target => {
      subscribeMonitorTick(muid1, target)(eventBus)(testActor)
      subscribeMonitorTick(muid2, target)(eventBus)(testActor)
    })

    EventFilter.info(occurrences = 1, source = monitor1.path.toString).intercept({
      monitor1 ! MonitorStart("test", muid1, targets)
    })

    EventFilter.info(occurrences = 1, source = monitor2.path.toString).intercept({
      monitor2 ! MonitorStart("test", muid2, targets)
    })

    var filter = EventFilter.custom({
      case Info(_, claz, _) if claz == classOf[ClockChild] || claz == classOf[MonitorChild] => true
    }, occurrences = 2)
    filter.intercept({
      monitor1 ! MonitorFrequency("test", muid1, frequency1)
    })
    messages = receiveWhile(10.seconds, messages = targets.size * ((10.seconds / frequency1) * threshold).toInt) {
      case msg: MonitorTick => msg
    }
    messages.size should equal(targets.size * ((10.seconds / frequency1) * threshold).toInt)
    messages.map(_.muid).toSet should contain theSameElementsAs Seq(muid1)
    messages.map(_.target).toSet should contain theSameElementsAs targets

    targets.foreach(target => unsubscribeMonitorTick(muid1, target)(eventBus)(testActor))
    filter = EventFilter.custom({
      case Info(_, claz, _) if claz == classOf[ClockChild] || claz == classOf[MonitorChild] => true
    }, occurrences = 2)
    filter.intercept({
      monitor2 ! MonitorFrequency("test", muid2, frequency2)
    })
    messages = receiveWhile(10.seconds, messages = targets.size * ((10.seconds / frequency2) * threshold).toInt) {
      case msg: MonitorTick => msg
    }
    messages.size should equal(targets.size * ((10.seconds / frequency2) * threshold).toInt)
    messages.map(_.muid).toSet should contain theSameElementsAs Seq(muid2)
    messages.map(_.target).toSet should contain theSameElementsAs targets

    filter = EventFilter.custom({
      case Info(_, claz, _) if claz == classOf[ClockChild] || claz == classOf[MonitorChild] => true
    }, occurrences = 3)
    filter.intercept({
      monitor2 ! MonitorFrequency("test", muid2, frequency3)
    })
    messages = receiveWhile(10.seconds, messages = targets.size * ((10.seconds / frequency3) * threshold).toInt) {
      case msg: MonitorTick => msg
    }
    messages.size should equal(targets.size * ((10.seconds / frequency3) * threshold).toInt)
    messages.map(_.muid).toSet should contain theSameElementsAs Seq(muid2)
    messages.map(_.target).toSet should contain theSameElementsAs targets

    val children = clocks.children.seq
    children.foreach(child => watcher.watch(child))

    monitor1 ! MonitorStop("test", muid1)
    monitor2 ! MonitorStopAll("test")

    watcher.receiveN(2 + children.size).asInstanceOf[Seq[Terminated]].map(_.actor) should contain theSameElementsAs Seq(monitor1, monitor2) ++ children

    Await.result(gracefulStop(clocks, timeout.duration), timeout.duration)
    Await.result(gracefulStop(monitor1, timeout.duration), timeout.duration)
    Await.result(gracefulStop(monitor2, timeout.duration), timeout.duration)
  }

  it should "aggregate its RawPowerReport into one AggregatePowerReport when all required RawPowerReport are stacked" in new Bus {
    val muid = UUID.randomUUID()
    val targets = Set[Target](1, "java", All)

    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }
    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 1000
    }
    val tick3 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 2000
    }
    val tick4 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 3000
    }

    val monitor = TestActorRef(Props(classOf[MonitorChild], eventBus, muid, targets), "monitor")
    val watcher = TestProbe()
    watcher.watch(monitor)

    subscribeAggPowerReport(muid)(eventBus)(testActor)

    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStart("test", muid, targets)
    })

    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorAggregator("test", muid, MEAN)
    })
    monitor ! RawPowerReport("test", muid, 1, 10.W, "cpu", tick1)
    monitor ! RawPowerReport("test", muid, "java", 5.W, "cpu", tick1)
    expectNoMsg()
    monitor ! RawPowerReport("test", muid, 1, 15.W, "cpu", tick2)
    var msg = expectMsgClass(classOf[AggregatePowerReport])
    msg.size should equal(2)
    msg.targets should contain theSameElementsAs Seq[Target](1, "java")
    msg.devices should contain theSameElementsAs Seq("cpu")
    msg.tick should equal(tick1)
    msg.power should equal(MEAN(Seq(10.W, 5.W)))
    monitor ! RawPowerReport("test", muid, 1, 12.W, "cpu", tick3)
    msg = expectMsgClass(classOf[AggregatePowerReport])
    msg.size should equal(1)
    msg.targets should contain theSameElementsAs Seq[Target](1)
    msg.devices should contain theSameElementsAs Seq("cpu")
    msg.tick should equal(tick2)
    msg.power should equal(MEAN(Seq(15.W)))
    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorAggregator("test", muid, MAX)
    })
    monitor ! RawPowerReport("test", muid, "java", 3.W, "cpu", tick3)
    monitor ! RawPowerReport("test", muid, All, 15.W, "cpu", tick3)
    monitor ! RawPowerReport("test", muid, 1, 15.W, "cpu", tick4)
    msg = expectMsgClass(classOf[AggregatePowerReport])
    msg.size should equal(3)
    msg.targets should contain theSameElementsAs Seq[Target](1, "java", All)
    msg.devices should contain theSameElementsAs Seq("cpu")
    msg.tick should equal(tick3)
    msg.power should equal(MAX(Seq(12.W, 3.W, 15.W)))

    Await.result(gracefulStop(monitor, timeout.duration), timeout.duration)
  }

  "A Monitors actor" should "handle MonitorChild actors" in new Bus {
    val muid1 = UUID.randomUUID()
    val frequency1 = 50.milliseconds
    val muid2 = UUID.randomUUID()
    val muid3 = UUID.randomUUID()

    val clocks = TestActorRef(Props(classOf[Clocks], eventBus), "clocks")
    val monitors = TestActorRef(Props(classOf[Monitors], eventBus), "monitors")
    val watcher = TestProbe()

    subscribeMonitorTick(muid1, 1)(eventBus)(testActor)
    subscribeMonitorTick(muid1, "java")(eventBus)(testActor)

    EventFilter.info(occurrences = 1, start = s"monitor is started, muid: $muid1").intercept({
      startMonitor(muid1, Set(1, "java"))(eventBus)
    })
    EventFilter.info(occurrences = 1, start = s"monitor is started, muid: $muid2").intercept({
      startMonitor(muid2, Set(2, "java", All))(eventBus)
    })
    EventFilter.info(occurrences = 1, start = s"monitor is started, muid: $muid3").intercept({
      startMonitor(muid3, Set(All))(eventBus)
    })

    var filter = EventFilter.custom({
      case Info(_, claz, _) if claz == classOf[ClockChild] || claz == classOf[MonitorChild] => true
    }, occurrences = 2)
    filter.intercept({
      setFrequency(muid1, frequency1)(eventBus)
    })

    receiveWhile(10.seconds, messages = (2 * (10.seconds / frequency1) * threshold).toInt) {
      case msg: MonitorTick => msg
    }.size should equal(2 * ((10.seconds / frequency1) * threshold).toInt)

    val clockChildren = clocks.children.seq
    val monitorChildren = monitors.children.seq
    clockChildren foreach watcher.watch
    monitorChildren foreach watcher.watch

    unsubscribeMonitorTick(muid1, 1)(eventBus)(testActor)
    unsubscribeMonitorTick(muid1, "java")(eventBus)(testActor)
    expectNoMsg()

    unsubscribeAggPowerReport(muid1)(eventBus)(testActor)
    stopMonitor(muid1)(eventBus)

    subscribeAggPowerReport(muid2)(eventBus)(testActor)
    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }
    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 1000
    }
    publishRawPowerReport(muid2, 2, 1.W, "cpu", tick1)(eventBus)
    publishRawPowerReport(muid2, "java", 5.W, "cpu", tick1)(eventBus)
    publishRawPowerReport(muid2, All, 6.W, "cpu", tick1)(eventBus)

    EventFilter.info(occurrences = 1, message = "aggregator is changed").intercept({
      setAggregator(muid2, MEDIAN)(eventBus)
    })

    publishRawPowerReport(muid2, 1, 10.W, "cpu", tick2)(eventBus)
    val msg = expectMsgClass(classOf[AggregatePowerReport])
    msg.size should equal(3)
    msg.devices should contain theSameElementsAs Seq("cpu")
    msg.power should equal(MEDIAN(Seq(1.W, 5.W, 6.W)))
    msg.targets should contain theSameElementsAs Seq[Target](2, "java", All)
    msg.tick should equal(tick1)

    stopAllMonitor(eventBus)

    watcher.receiveN(4).asInstanceOf[Seq[Terminated]].map(_.actor) should contain theSameElementsAs clockChildren ++ monitorChildren
    watcher.expectNoMsg()

    Await.result(gracefulStop(clocks, timeout.duration), timeout.duration)
    Await.result(gracefulStop(monitors, timeout.duration), timeout.duration)
  }

  "A Monitor object" should "allow to interact directly with the Monitor supervisor" in new Bus {
    val monitorO = new Monitor(eventBus)
    val frequency = 50.milliseconds
    val clocks = TestActorRef(Props(classOf[Clocks], eventBus), "clocks")
    val monitors = TestActorRef(Props(classOf[Monitors], eventBus), "monitors")
    val sensors = TestActorRef(Props(classOf[Sensors], eventBus), "sensors")
    val formulas = TestActorRef(Props(classOf[Formulas], eventBus), "formulas")
    val reporters = TestActorRef(Props(classOf[Reporters], eventBus), "reporters")

    val watcher = TestProbe()

    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }
    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 1000
    }
    val tick3 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 2000
    }
    val tick4 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 3000
    }
    val tick5 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 4000
    }

    val reporter = new DisplayMock

    class DisplayMock extends PowerDisplay {
      def display(muid: UUID, timestamp: Long, targets: Set[Target], devices: Set[String], power: Power): Unit = testActor ! power
    }

    EventFilter.info(occurrences = 1, start = s"monitor is started, muid: ${monitorO.muid}").intercept({
      startMonitor(monitorO.muid, Set(1, "java"))(eventBus)
    })

    EventFilter.info(occurrences = 2, start = s"sensor is started, class: ${classOf[EmptySensor].getName}").intercept({
      startSensor(monitorO.muid, Process(1), classOf[EmptySensor], Seq(eventBus, monitorO.muid, Process(1)))(eventBus)
      startSensor(monitorO.muid, Application("java"), classOf[EmptySensor], Seq(eventBus, monitorO.muid, Application("java")))(eventBus)
    })

    EventFilter.info(occurrences = 2, start = s"formula is started, class: ${classOf[EmptyFormula].getName}").intercept({
      startFormula(monitorO.muid, Process(1), classOf[EmptyFormula], Seq(eventBus, monitorO.muid, Process(1)))(eventBus)
      startFormula(monitorO.muid, Application("java"), classOf[EmptyFormula], Seq(eventBus, monitorO.muid, Application("java")))(eventBus)
    })

    EventFilter.info(occurrences = 1, message = "aggregator is changed").intercept({
      monitorO(MAX)
    })

    EventFilter.info(occurrences = 1, start = s"reporter is started, class: ${reporter.getClass.getName}").intercept({
      monitorO.to(reporter)
    })
    val reporterChildren = reporters.children.seq
    reporterChildren foreach watcher.watch
    publishRawPowerReport(monitorO.muid, 1, 1.W, "cpu", tick1)(eventBus)
    publishRawPowerReport(monitorO.muid, "java", 5.W, "cpu", tick1)(eventBus)
    publishRawPowerReport(monitorO.muid, "java", 10.W, "cpu", tick2)(eventBus)
    expectMsgClass(classOf[RawPower]) should equal(MAX(Seq(1.W, 5.W)))
    EventFilter.info(occurrences = 1, start = s"reporter is stopped, class: ${reporter.getClass.getName}").intercept({
      monitorO.unto(reporter)
    })
    watcher.receiveN(1).asInstanceOf[Seq[Terminated]].map(_.actor) should contain theSameElementsAs reporterChildren
    monitorO.to(testActor)
    publishRawPowerReport(monitorO.muid, 1, 6.W, "cpu", tick2)(eventBus)
    publishRawPowerReport(monitorO.muid, 1, 5.W, "cpu", tick3)(eventBus)
    expectMsgClass(classOf[AggregatePowerReport]).power should equal(MAX(Seq(6.W, 10.W)))
    expectNoMsg()
    monitorO.unto(testActor)
    monitorO.to(testActor, subscribeAggPowerReport(monitorO.muid))
    publishRawPowerReport(monitorO.muid, "java", 1.W, "cpu", tick3)(eventBus)
    publishRawPowerReport(monitorO.muid, "java", 4.W, "cpu", tick4)(eventBus)
    expectMsgClass(classOf[AggregatePowerReport]).power should equal(MAX(Seq(5.W, 1.W)))
    expectNoMsg()
    monitorO.unto(testActor, unsubscribeAggPowerReport(monitorO.muid))
    publishRawPowerReport(monitorO.muid, "java", 4.W, "cpu", tick5)(eventBus)
    expectNoMsg()

    subscribeMonitorTick(monitorO.muid, 1)(eventBus)(testActor)
    subscribeMonitorTick(monitorO.muid, "java")(eventBus)(testActor)

    val filter = EventFilter.custom({
      case Info(_, claz, _) if claz == classOf[ClockChild] || claz == classOf[MonitorChild] => true
    }, occurrences = 2)
    filter.intercept({
      monitorO.every(frequency)
    })

    receiveWhile(10.seconds, messages = (2 * (10.seconds / frequency) * threshold).toInt) {
      case msg: MonitorTick => msg
    }.size should equal(2 * ((10.seconds / frequency) * threshold).toInt)

    val clockChildren = clocks.children.seq
    val monitorChildren = monitors.children.seq
    val sensorChildren = sensors.children.seq
    val formulaChildren = formulas.children.seq
    clockChildren foreach watcher.watch
    monitorChildren foreach watcher.watch
    sensorChildren foreach watcher.watch
    formulaChildren foreach watcher.watch

    monitorO.cancel()

    watcher.receiveN(6).asInstanceOf[Seq[Terminated]].map(_.actor) should contain theSameElementsAs clockChildren ++ monitorChildren ++ sensorChildren ++ formulaChildren

    Await.result(gracefulStop(clocks, timeout.duration), timeout.duration)
    Await.result(gracefulStop(monitors, timeout.duration), timeout.duration)
    Await.result(gracefulStop(sensors, timeout.duration), timeout.duration)
    Await.result(gracefulStop(formulas, timeout.duration), timeout.duration)
    Await.result(gracefulStop(reporters, timeout.duration), timeout.duration)
  }
}
