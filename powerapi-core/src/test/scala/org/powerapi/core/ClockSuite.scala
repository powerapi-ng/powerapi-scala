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

import akka.actor.{Actor, ActorNotFound, ActorRef, ActorSystem, Props}
import akka.pattern.gracefulStop
import akka.testkit.{EventFilter, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.powerapi.UnitTest
import org.powerapi.core.ClockChannel.{ClockTick, ClockStart, ClockStop, formatClockChildName, startClock, stopClock, subscribeClockTick, unsubscribeClockTick}
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}

class EchoClockTickActor(eventBus: MessageBus, testActor: ActorRef, frequency: FiniteDuration) extends Actor {
  override def preStart(): Unit = {
    subscribeClockTick(frequency)(eventBus)(testActor)
  }

  def receive = {
    case msg => testActor ! msg
  }
}

class ClockSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("ClockSuite"))

  val eventListener = ConfigFactory.parseResources("test.conf")

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "The Clock actors" should "launch an exception when the messages received cannot be handled" in new Bus {
    val _system = ActorSystem("ClockSuiteTest1", eventListener)

    val frequency = 50.milliseconds
    val clock = _system.actorOf(Props(classOf[ClockChild], eventBus, frequency), "clock1")

    EventFilter.warning(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStop("test", frequency)
    })(_system)

    EventFilter.warning(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStart("test", Duration.Zero)
    })(_system)

    // Not an exception, just an assertion (switching in the running state).
    EventFilter.info(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStart("test", frequency)
    })(_system)

    EventFilter.warning(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStart("test", Duration.Zero)
    })(_system)

    EventFilter.warning(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStop("test", Duration.Zero)
    })(_system)

    EventFilter.info(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStop("test", frequency)
    })(_system)
    
    Await.result(gracefulStop(clock, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  "A ClockChild actor" should "produce Ticks, stop its own timer if needed and thus stop to publish Ticks" in new Bus {
    val _system = ActorSystem("ClockSuiteTest2", eventListener)

    val frequency = 25.milliseconds
    var messages = Seq[ClockTick]()

    val clock = _system.actorOf(Props(classOf[ClockChild], eventBus, frequency), "clock2")
    subscribeClockTick(frequency)(eventBus)(testActor)

    val watcher = TestProbe()(_system)
    watcher.watch(clock)

    EventFilter.info(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStart("test", frequency)
    })(_system)

    receiveWhile(20.seconds, messages = 10) {
      case msg: ClockTick => messages :+= msg
    }
    
    EventFilter.info(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStop("test", frequency)
    })(_system)

    awaitAssert({
      watcher.expectTerminated(clock)
    }, 20.seconds)

    messages.size should equal(10)

    receiveWhile(10.seconds, idle = 2.seconds) {
      case msg: ClockTick => {}
    }
    
    Await.result(gracefulStop(clock, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  it should "handle only one timer and stop it if there is no subscription" in new Bus {
    val _system = ActorSystem("ClockSuiteTest3", eventListener)

    val frequency = 25.milliseconds
    var messages = Seq[ClockTick]()

    val clock = _system.actorOf(Props(classOf[ClockChild], eventBus, frequency), "clock3")
    subscribeClockTick(frequency)(eventBus)(testActor)

    val watcher = TestProbe()(_system)
    watcher.watch(clock)

    EventFilter.info(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStart("test", frequency)
    })(_system)

    EventFilter.info(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStart("test", frequency)
    })(_system)

    EventFilter.info(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStop("test", frequency)
    })(_system)

    receiveWhile(20.seconds, messages = 10) {
      case msg: ClockTick => messages :+= msg
    }

    EventFilter.info(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStop("test", frequency)
    })(_system)

    awaitAssert({
      watcher.expectTerminated(clock)
    }, 20.seconds)

    messages.size should equal(10)

    receiveWhile(10.seconds, idle = 2.seconds) {
      case msg: ClockTick => {}
    }

    Await.result(gracefulStop(clock, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  "A Clocks actor" should "handle ClockChild actors and the subscribers have to receive tick messages for their frequencies" in new Bus {
    val _system = ActorSystem("ClockSuiteTest4")

    val frequency1 = 50.milliseconds
    val frequency2 = 100.milliseconds
    val frequency3 = 150.milliseconds
    var messages = Seq[ClockTick]()

    val clocks = _system.actorOf(Props(classOf[Clocks], eventBus), "clocks4")

    val nbSubscribers = 100

    for(i <- 0 until nbSubscribers) {
      _system.actorOf(Props(classOf[EchoClockTickActor], eventBus, testActor, frequency1), s"subscriberF1-$i")
      _system.actorOf(Props(classOf[EchoClockTickActor], eventBus, testActor, frequency2), s"subscriberF2-$i")
      _system.actorOf(Props(classOf[EchoClockTickActor], eventBus, testActor, frequency3), s"subscriberF3-$i")
    }

    startClock(frequency1)(eventBus)
    startClock(frequency2)(eventBus)
    startClock(frequency2)(eventBus)
    startClock(frequency3)(eventBus)

    receiveWhile(40.seconds, messages = 300) {
      case msg: ClockTick => messages :+= msg
    }

    stopClock(frequency1)(eventBus)
    stopClock(frequency2)(eventBus)
    stopClock(frequency2)(eventBus)
    stopClock(frequency3)(eventBus)

    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(s"user/clocks4/${formatClockChildName(frequency1)}").resolveOne(), timeout.duration)
      }
    }, 20.seconds)
    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(s"user/clocks4/${formatClockChildName(frequency2)}").resolveOne(), timeout.duration)
      }
    }, 20.seconds)
    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(s"user/clocks4/${formatClockChildName(frequency3)}").resolveOne(), timeout.duration)
      }
    }, 20.seconds)

    messages.size should equal(300)
    messages = Seq()

    unsubscribeClockTick(frequency1)(eventBus)(Await.result(_system.actorSelection("user/subscriberF1-0").resolveOne(), timeout.duration))
    startClock(frequency1)(eventBus)

    receiveWhile(30.seconds, messages = 99) {
      case msg: ClockTick => messages :+= msg
    }

    stopClock(frequency1)(eventBus)

    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(s"user/clocks4/${formatClockChildName(frequency1)}").resolveOne(), timeout.duration)
      }
    }, 20.seconds)

    messages.size should equal(99)

    receiveWhile(10.seconds, idle = 2.seconds) {
      case msg: ClockTick => {}
    }

    Await.result(gracefulStop(clocks, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  it can "handle a large number of clocks and the subscribers have to receive tick messages for their frequencies" in new Bus {
    val _system = ActorSystem("ClockSuiteTest5")

    val clocks = _system.actorOf(Props(classOf[Clocks], eventBus), "clocks5")

    val frequencies = scala.collection.mutable.ArrayBuffer[FiniteDuration]()
    var messages = Seq[ClockTick]()
    var nbMessages = 0

    for(i <- 10 until 50) {
      val frequency = i.milliseconds
      _system.actorOf(Props(classOf[EchoClockTickActor], eventBus, testActor, frequency), s"subscriberF$i")
      startClock(frequency)(eventBus)
      frequencies += frequency
      nbMessages += 250 / frequency.toMillis.toInt
    }

    receiveWhile(20.seconds, messages = nbMessages) {
      case msg: ClockTick => messages :+= msg
    }

    for(frequency <- frequencies) {
      stopClock(frequency)(eventBus)
    }

    messages.size should equal(nbMessages)

    receiveWhile(10.seconds, idle = 2.seconds) {
      case msg: ClockTick => {}
    }

    Await.result(gracefulStop(clocks, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }
}
