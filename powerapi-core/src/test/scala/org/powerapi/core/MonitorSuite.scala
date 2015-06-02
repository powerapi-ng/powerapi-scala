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
package org.powerapi.core

import akka.actor.{Props, ActorSystem, Actor, ActorRef, ActorNotFound, Terminated, Identify, ActorIdentity}
import akka.pattern.{ask, gracefulStop}
import akka.testkit.{TestActorRef, TestProbe, EventFilter, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import java.util.UUID
import org.powerapi.{PowerDisplay, UnitTest}
import org.powerapi.core.ClockChannel.{ ClockTick, formatClockChildName }
import org.powerapi.core.MonitorChannel.{ MonitorAggFunction, MonitorStart, MonitorStop, MonitorTick, subscribeMonitorTick, formatMonitorChildName, startMonitor, stopAllMonitor, stopMonitor }
import org.powerapi.core.power._
import org.powerapi.core.target.{All, intToProcess, stringToApplication, Target, Process}
import org.powerapi.module.PowerChannel.{AggregatePowerReport, publishRawPowerReport, subscribeAggPowerReport}
import org.powerapi.module.SensorChannel.subscribeSensorsChannel
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}

class EchoMonitorTickActor(eventBus: MessageBus, testActor: ActorRef) extends Actor {

  override def preStart(): Unit = {
    subscribeMonitorTick(eventBus)(testActor)
  }

  def receive = {
    case msg => testActor ! msg
  }
}

class MonitorSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("MonitorSuite"))

  val eventListener = ConfigFactory.parseResources("test.conf")

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "The Monitor actors" should "launch an exception when the messages received cannot be handled" in new Bus {
    val _system = ActorSystem("MonitorSuiteTest1", eventListener)

    val muid = UUID.randomUUID()
    val frequency = 50.milliseconds
    val targets = List[Target](1)
                                                            
    val monitor = _system.actorOf(Props(classOf[MonitorChild], eventBus, muid, frequency, targets), "monitor1")

    EventFilter.warning(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStop("test", muid)
    })(_system)

    EventFilter.warning(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStart("test", muid, Duration.Zero, targets)
    })(_system)

    // Not an exception, just an assessment (switching in the running state).
    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStart("test", muid, frequency, targets)
    })(_system)

    EventFilter.warning(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStart("test", muid, frequency, targets)
    })(_system)

    EventFilter.warning(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStop("test", UUID.randomUUID())
    })(_system)

    receiveWhile(10.seconds, idle = 2.seconds) {
      case msg: MonitorTick => {}
    }

    Await.result(gracefulStop(monitor, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  "A MonitorChild actor" should "start to listen ticks for its frequency and produce messages" in new Bus {
    val _system = ActorSystem("MonitorSuiteTest2", eventListener)
    val clocks = _system.actorOf(Props(classOf[Clocks], eventBus), "clocks2")

    val frequency = 25.milliseconds
    val muid = UUID.randomUUID()
    val targets = List[Target](1, "java", All)
    var messages = Seq[MonitorTick]()
    
    val monitor = _system.actorOf(Props(classOf[MonitorChild], eventBus, muid, frequency, targets), "monitor2")
    subscribeMonitorTick(eventBus)(testActor)
    val watcher = TestProbe()(_system)
    watcher.watch(monitor)

    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStart("test", muid, frequency, targets)
    })(_system)

    receiveWhile(20.seconds, messages = 10) {
      case msg: MonitorTick => messages :+= msg
    }

    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStop("test", muid)
    })(_system)

    awaitAssert({
      watcher.expectTerminated(monitor)
    }, 20.seconds)

    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(s"user/clocks2/${formatClockChildName(frequency)}").resolveOne(), timeout.duration)
      }
    }, 20.seconds)

    messages.size should equal(10)

    receiveWhile(10.seconds, idle = 2.seconds) {
      case msg: MonitorTick => {}
    }

    Await.result(gracefulStop(clocks, timeout.duration), timeout.duration)
    Await.result(gracefulStop(monitor, timeout.duration), timeout.duration)
    Await.result(gracefulStop(watcher.ref, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  it can "handle a large number of targets" in new Bus {
    val _system = ActorSystem("MonitorSuiteTest3")

    val frequency = 25.milliseconds
    val muid = UUID.randomUUID()
    val targets = scala.collection.mutable.ListBuffer[Target]()
    var messages = Seq[MonitorTick]()

    for(i <- 1 to 100) {
      targets += i
    }

    val clocks = _system.actorOf(Props(classOf[Clocks], eventBus), "clocks3")
    val monitor = _system.actorOf(Props(classOf[MonitorChild], eventBus, muid, frequency, targets.toList), "monitor3")
    subscribeMonitorTick(eventBus)(testActor)
    val watcher = TestProbe()(_system)
    watcher.watch(monitor)

    monitor ! MonitorStart("test", muid, frequency, targets.toList)
    receiveWhile(20.seconds, messages = 100) {
      case msg: MonitorTick => messages :+= msg
    }
    monitor ! MonitorStop("test", muid)

    awaitAssert({
      watcher.expectTerminated(monitor)
    }, 20.seconds)

    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(s"user/clocks3/${formatClockChildName(frequency)}").resolveOne(), timeout.duration)
      }
    }, 20.seconds)

    messages.size should equal(100)

    receiveWhile(10.seconds, idle = 2.seconds) {
      case msg: MonitorTick => {}
    }

    Await.result(gracefulStop(clocks, timeout.duration), timeout.duration)
    Await.result(gracefulStop(monitor, timeout.duration), timeout.duration)
    Await.result(gracefulStop(watcher.ref, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  "A Monitors actor" should "handle its MonitorChild actors and subscribers have to receive messages" in new Bus {
    val _system = ActorSystem("MonitorSuiteTest4")

    val clocks = _system.actorOf(Props(classOf[Clocks], eventBus), "clocks4")
    val monitors = _system.actorOf(Props(classOf[Monitors], eventBus), "monitors4")
    
    val frequency = 25.milliseconds
    val targets = List[Target](1, "java")
    var messages = Seq[MonitorTick]()
                                                      
    val monitor = new Monitor(eventBus, _system)

    for(i <- 0 until 100) {
      _system.actorOf(Props(classOf[EchoMonitorTickActor], eventBus, testActor))
    }

    startMonitor(monitor.muid, frequency, targets)(eventBus)
    receiveWhile(40.seconds, messages = 400) {
      case msg: MonitorTick => messages :+= msg
    }
    monitor.cancel()

    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(s"user/monitors4/${formatMonitorChildName(monitor.muid)}").resolveOne(), timeout.duration)
      }
    }, 20.seconds)

    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(s"user/clocks4/${formatClockChildName(frequency)}").resolveOne(), timeout.duration)
      }
    }, 20.seconds)

    messages.size should equal(400)

    receiveWhile(10.seconds, idle = 2.seconds) {
      case msg: MonitorTick => {}
    }
    
    Await.result(gracefulStop(clocks, timeout.duration), timeout.duration)
    Await.result(gracefulStop(monitors, timeout.duration), timeout.duration)  
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  it should "publish a message to the sensor actors for let them know that the monitor(s) is/are stopped" in new Bus {
    val _system = ActorSystem("MonitorSuiteTest5")

    val monitors = TestActorRef(Props(classOf[Monitors], eventBus), "monitors5")(_system)
    val reaper = TestProbe()(system)
    subscribeSensorsChannel(eventBus)(testActor)

    val monitor = new Monitor(eventBus, _system)
    val monitor2 = new Monitor(eventBus, _system)

    Await.result(monitors ? MonitorStart("", monitor.muid, 25.milliseconds, List[Target](1)), timeout.duration)
    startMonitor(monitor2.muid, 50.milliseconds, List[Target](2))(eventBus)

    awaitAssert({
      Await.result(_system.actorSelection(s"user/monitors5/${formatMonitorChildName(monitor.muid)}") ? akka.actor.Identify(None), timeout.duration) match {
        case akka.actor.ActorIdentity(_, Some(_)) => true
        case _ => false
      }
    }, 20.seconds)

    awaitAssert({
      Await.result(_system.actorSelection(s"user/monitors5/${formatMonitorChildName(monitor2.muid)}") ? akka.actor.Identify(None), timeout.duration) match {
        case akka.actor.ActorIdentity(_, Some(_)) => true
        case _ => false
      }
    }, 20.seconds)

    val children = monitors.children.toArray.clone()
    children.foreach(child => reaper.watch(child))

    stopMonitor(monitor.muid)(eventBus)
    stopAllMonitor(eventBus)
    for(_ <- 0 until children.size) {
      reaper.expectMsgClass(classOf[Terminated])
    }

    expectMsgClass(classOf[org.powerapi.module.SensorChannel.MonitorStop]) match {
      case org.powerapi.module.SensorChannel.MonitorStop(_, id) => monitor.muid should equal(id)
    }
    expectMsgClass(classOf[org.powerapi.module.SensorChannel.MonitorStopAll])

    receiveWhile(10.seconds, idle = 2.seconds) {
      case msg: MonitorTick => {}
    }

    Await.result(gracefulStop(monitors, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  it should "handle a large number of monitors" in new Bus {
    val _system = ActorSystem("MonitorSuiteTest5")
    val clocks = _system.actorOf(Props(classOf[Clocks], eventBus), "clocks5")
    val monitors = _system.actorOf(Props(classOf[Monitors], eventBus), "monitors5")

    val targets = List(All)
    var messages = Seq[MonitorTick]()
    var monitorObjects = Seq[Monitor]()
    var nbMessages = 0

    for(frequency <- 10 to 30) {
      val monitor = new Monitor(eventBus, _system)
      monitorObjects :+= monitor
      startMonitor(monitor.muid, frequency.milliseconds, targets)(eventBus)
      _system.actorOf(Props(classOf[EchoMonitorTickActor], eventBus, testActor), s"subscriberF$frequency")
      nbMessages += (80 / frequency) * targets.size
    }

    receiveWhile(40.seconds, messages = nbMessages) {
      case msg: MonitorTick => messages :+= msg
    }

    for(monitor <- monitorObjects) {
      monitor.cancel()
    }

    receiveWhile(10.seconds, idle = 2.seconds) {
      case msg: MonitorTick => {}
    }

    Await.result(gracefulStop(clocks, timeout.duration), timeout.duration)
    Await.result(gracefulStop(monitors, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  "A MonitorChild actor" should "start to listen power reports for its MUID and produce aggregated messages" in new Bus {
    val _system = ActorSystem("MonitorSuiteTest6", eventListener)

    val muid = UUID.randomUUID()
    val frequency = 25.milliseconds
    val targets = List[Target](1, 2, 3)

    val device = "mock"
    val tickMock = ClockTick("ticktest", frequency)

    val monitor = _system.actorOf(Props(classOf[MonitorChild], eventBus, muid, frequency, targets), "monitor6")
    val watcher = TestProbe()(_system)
    watcher.watch(monitor)
    subscribeAggPowerReport(muid)(eventBus)(testActor)

    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStart("test", muid, frequency, targets)
    })(_system)
    
    publishRawPowerReport(muid, 1, 4.W, device, tickMock)(eventBus)
    publishRawPowerReport(muid, 2, 5.W, device, tickMock)(eventBus)
    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorAggFunction("test", muid, MAX)
    })(_system)
    publishRawPowerReport(muid, 3, 6.W, device, tickMock)(eventBus)

    publishRawPowerReport(muid, 1, 11.W, device, tickMock)(eventBus)
    publishRawPowerReport(muid, 2, 10.W, device, tickMock)(eventBus)
    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorAggFunction("test", muid, MIN)
    })(_system)
    publishRawPowerReport(muid, 3, 12.W, device, tickMock)(eventBus)
    
    publishRawPowerReport(muid, 1, 9.W, device, tickMock)(eventBus)
    publishRawPowerReport(muid, 2, 3.W, device, tickMock)(eventBus)
    publishRawPowerReport(muid, 3, 7.W, device, tickMock)(eventBus)

    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStop("test", muid)
    })(_system)

    awaitAssert({
      watcher.expectTerminated(monitor)
    }, 20.seconds)

    expectMsgClass(classOf[AggregatePowerReport]).power should equal(15.W)
    expectMsgClass(classOf[AggregatePowerReport]).power should equal(12.W)
    expectMsgClass(classOf[AggregatePowerReport]).power should equal(3.W)

    receiveWhile(10.seconds, idle = 2.seconds) {
      case msg: MonitorTick => {}
    }

    Await.result(gracefulStop(monitor, timeout.duration), timeout.duration)
    Await.result(gracefulStop(watcher.ref, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  it can "handle a large number of power reports" in new Bus {
    val _system = ActorSystem("MonitorSuiteTest7", eventListener)

    val muid = UUID.randomUUID()
    val frequency = 25.milliseconds
    val targets = scala.collection.mutable.ListBuffer[Target]()

    val device = "mock"
    val tickMock = ClockTick("ticktest", frequency)

    for(i <- 1 to 50) {
      targets += i
    }

    val monitor = _system.actorOf(Props(classOf[MonitorChild], eventBus, muid, frequency, targets.toList), "monitor7")
    val watcher = TestProbe()(_system)
    watcher.watch(monitor)
    subscribeAggPowerReport(muid)(eventBus)(testActor)

    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStart("test", muid, frequency, targets.toList)
    })(_system)

    for(i <- 1 to 150) {
      publishRawPowerReport(muid, i, (i * 3.0).W, device, tickMock)(eventBus)
    }

    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStop("test", muid)
    })(_system)

    awaitAssert({
      watcher.expectTerminated(monitor)
    }, 20.seconds)

    expectMsgClass(classOf[AggregatePowerReport]).power should equal(3825.W)
    expectMsgClass(classOf[AggregatePowerReport]).power should equal(11325.W)
    expectMsgClass(classOf[AggregatePowerReport]).power should equal(18825.W)

    receiveWhile(10.seconds, idle = 2.seconds) {
      case msg: MonitorTick => {}
    }

    Await.result(gracefulStop(monitor, timeout.duration), timeout.duration)
    Await.result(gracefulStop(watcher.ref, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  "A Monitor object" should "allow to interact directly with the bus" in new Bus {
    val _system = ActorSystem("MonitorSuiteTest8", eventListener)
    val reporters = ActorSystem("Reporters8", eventListener)

    val monitors = _system.actorOf(Props(classOf[Monitors], eventBus), "monitors8")

    val tickMock = ClockTick("ticktest", 1.second)
    val monitor = new Monitor(eventBus, reporters)
    val monitor2 = new Monitor(eventBus, reporters)
    val monitor3 = new Monitor(eventBus, reporters)

    val display = new PowerDisplay {
      def display(muid: UUID, timestamp: Long, targets: Set[Target], devices: Set[String], power: Power): Unit = {
        testActor ! s"$muid, $timestamp, ${targets.mkString(",")}, ${devices.mkString(",")}, $power"
      }
    }

    Await.result(monitors.ask(MonitorStart("", monitor.muid, 1.seconds, List(1)))(timeout), timeout.duration)
    Await.result(monitors.ask(MonitorStart("", monitor2.muid, 1.seconds, List(2)))(timeout), timeout.duration)
    Await.result(monitors.ask(MonitorStart("", monitor3.muid, 1.seconds, List(1, 3)))(timeout), timeout.duration)

    monitor.to(display)
    publishRawPowerReport(monitor.muid, 1, 15.W, "gpu", tickMock)(eventBus)
    expectMsgClass(classOf[String]) should equal(s"${monitor.muid}, ${tickMock.timestamp}, ${Process(1)}, gpu, ${15000.mW}")
    reporters.actorSelection("user/*") ! Identify(None)
    val reporter = expectMsgClass(classOf[ActorIdentity]).getRef

    monitor2.to(testActor, subscribeSensorsChannel)
    monitors ! MonitorStop("", monitor2.muid)
    expectMsgClass(classOf[org.powerapi.module.SensorChannel.MonitorStop]).muid should equal(monitor2.muid)

    monitor3.to(testActor)
    publishRawPowerReport(monitor3.muid, 1, 1.W, "gpu", tickMock)(eventBus)
    publishRawPowerReport(monitor3.muid, 3, 15.W, "gpu", tickMock)(eventBus)
    expectMsgClass(classOf[AggregatePowerReport]).power should equal(16.W)

    monitor.cancel()
    monitor3.cancel()

    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(reporters.actorSelection(reporter.path).resolveOne(), timeout.duration)
      }
    }, 20.seconds)

    Await.result(gracefulStop(monitors, timeout.duration), timeout.duration)
    reporters.shutdown()
    reporters.awaitTermination(timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }
}
