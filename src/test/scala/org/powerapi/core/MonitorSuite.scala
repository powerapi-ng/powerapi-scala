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
package org.powerapi.core

import java.util.UUID
import akka.actor.{Actor, ActorNotFound, ActorRef, ActorSystem, Props}
import akka.pattern.gracefulStop
import akka.testkit.{EventFilter, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.powerapi.UnitTest
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}

class MonitorMockSubscriber(eventBus: MessageBus) extends Actor {
  import org.powerapi.core.MonitorChannel.{MonitorTick, subscribeMonitorTick}

  override def preStart() = {
    subscribeMonitorTick(eventBus)(self)
    super.preStart()
  }

  def receive = active(0)

  def active(acc: Int): Actor.Receive = {
    case _: MonitorTick => context become active(acc + 1)
    case "reset" => context become active(0)
    case "get" => sender ! acc
  }
}

class MonitorSuite(system: ActorSystem) extends UnitTest(system) {
  import org.powerapi.core.ClockChannel.formatClockChildName
  import org.powerapi.core.MonitorChannel.{MonitorStart, MonitorStop, formatMonitorChildName, startMonitor, stopMonitor}

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
    val targets = List(Process(1))
    val monitor = _system.actorOf(Props(classOf[MonitorChild], eventBus, muid, frequency, targets), "monitor1")
    val monitors = _system.actorOf(Props(classOf[Monitors], eventBus), "monitors1")

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

    EventFilter.warning(occurrences = 1, source = monitors.path.toString).intercept({
      stopMonitor(muid)(eventBus)
    })(_system)

    Await.result(gracefulStop(monitor, timeout.duration), timeout.duration)
    Await.result(gracefulStop(monitors, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  "A MonitorChild actor" should "start to listen ticks for its frequency and produce messages" in new Bus {
    import java.lang.Thread

    val _system = ActorSystem("MonitorSuiteTest2", eventListener)
    val clocks = _system.actorOf(Props(classOf[Clocks], eventBus), "clocks2")

    val frequency = 25.milliseconds
    val muid = UUID.randomUUID()
    val targets = List(Process(1), Application("java"), All)
    
    val monitor = _system.actorOf(Props(classOf[MonitorChild], eventBus, muid, frequency, targets), "monitor2")
    val subscriber = _system.actorOf(Props(classOf[MonitorMockSubscriber], eventBus))
    val watcher = TestProbe()(_system)
    watcher.watch(monitor)

    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStart("test", muid, frequency, targets)
    })(_system)

    Thread.sleep(250)

    EventFilter.info(occurrences = 1, source = monitor.path.toString).intercept({
      monitor ! MonitorStop("test", muid)
    })(_system)

    awaitAssert({
      watcher.expectTerminated(monitor)
    }, 20.seconds)

    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(formatClockChildName(frequency)).resolveOne(), timeout.duration)
      }
    }, 20.seconds)

    subscriber ! "get"
    // We assume a service quality of 90% (regarding the number of processed messages).
    expectMsgClass(classOf[Int]) should be >= (targets.size * (10 * 0.9)).toInt

    Await.result(gracefulStop(clocks, timeout.duration), timeout.duration)
    Await.result(gracefulStop(monitor, timeout.duration), timeout.duration)
    Await.result(gracefulStop(subscriber, timeout.duration), timeout.duration)
    Await.result(gracefulStop(watcher.ref, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  it can "handle a large number of targets" in new Bus {
    import java.lang.Thread

    val _system = ActorSystem("MonitorSuiteTest3")

    val frequency = 25.milliseconds
    val muid = UUID.randomUUID()
    val targets = scala.collection.mutable.ListBuffer[Target]()

    for(i <- 1 to 100) {
      targets += Process(i)
    }

    val clocks = _system.actorOf(Props(classOf[Clocks], eventBus), "clocks3")
    val monitor = _system.actorOf(Props(classOf[MonitorChild], eventBus, muid, frequency, targets.toList), "monitor3")
    val subscriber = _system.actorOf(Props(classOf[MonitorMockSubscriber], eventBus))
    val watcher = TestProbe()(_system)
    watcher.watch(monitor)

    monitor ! MonitorStart("test", muid, frequency, targets.toList)
    Thread.sleep(250)
    monitor ! MonitorStop("test", muid)

    awaitAssert({
      watcher.expectTerminated(monitor)
    }, 20.seconds)

    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(formatClockChildName(frequency)).resolveOne(), timeout.duration)
      }
    }, 20.seconds)

    subscriber ! "get"
    // We assume a service quality of 90% (regarding the number of processed messages).
    expectMsgClass(classOf[Int]) should be >= (targets.size * (10 * 0.9)).toInt

    Await.result(gracefulStop(clocks, timeout.duration), timeout.duration)
    Await.result(gracefulStop(monitor, timeout.duration), timeout.duration)
    Await.result(gracefulStop(subscriber, timeout.duration), timeout.duration)
    Await.result(gracefulStop(watcher.ref, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  "A Monitors actor" should "handle its MonitorChild actors and subscribers have to receive messages" in new Bus {
    import java.lang.Thread

    val _system = ActorSystem("MonitorSuiteTest4")

    val clocks = _system.actorOf(Props(classOf[Clocks], eventBus), "clocks4")
    val monitors = _system.actorOf(Props(classOf[Monitors], eventBus), "monitors4")

    val targets = List(Process(1), Application("java"))
    val monitor = new Monitor(eventBus, targets)
    val frequency = 25.milliseconds

    val subscribers = scala.collection.mutable.ListBuffer[ActorRef]()

    for(i <- 0 until 100) {
      subscribers += _system.actorOf(Props(classOf[MonitorMockSubscriber], eventBus))
    }

    startMonitor(monitor.muid, frequency, targets)(eventBus)
    Thread.sleep(250)
    monitor.cancel()

    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(formatMonitorChildName(monitor.muid)).resolveOne(), timeout.duration)
      }
    }, 20.seconds)

    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(formatClockChildName(frequency)).resolveOne(), timeout.duration)
      }
    }, 20.seconds)

    for(i <- 0 until 100) {
      subscribers(i) ! "get"
      // We assume a service quality of 90% (regarding the number of processed messages).
      expectMsgClass(classOf[Int]) should be >= (targets.size * (10 * 0.9)).toInt
      Await.result(gracefulStop(subscribers(i), timeout.duration), timeout.duration)
    }
    
    Await.result(gracefulStop(clocks, timeout.duration), timeout.duration)
    Await.result(gracefulStop(monitors, timeout.duration), timeout.duration)  
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  it should "handle a large number of monitors" in new Bus {
    import java.lang.Thread

    val _system = ActorSystem("MonitorSuiteTest5")
    val clocks = _system.actorOf(Props(classOf[Clocks], eventBus), "clocks5")
    val monitors = _system.actorOf(Props(classOf[Monitors], eventBus), "monitors5")

    val targets = List(All)

    for(frequency <- 50 to 100) {
      val monitor = new Monitor(eventBus, targets)
      startMonitor(monitor.muid, frequency.milliseconds, targets)(eventBus)
    }

    Thread.sleep(1000)

    Await.result(gracefulStop(clocks, timeout.duration), timeout.duration)
    Await.result(gracefulStop(monitors, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }
}
