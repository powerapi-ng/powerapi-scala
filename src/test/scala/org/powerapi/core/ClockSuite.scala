/**
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

 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */

package org.powerapi.core

import org.powerapi.test.UnitTest

import scala.concurrent.Await
import scala.concurrent.duration.{ Duration, DurationInt, FiniteDuration }

import akka.actor.{ Actor, ActorNotFound, ActorRef, ActorSystem, Props }
import akka.pattern.gracefulStop
import akka.util.Timeout
import akka.testkit.{ EventFilter, TestKit, TestProbe }

import com.typesafe.config.ConfigFactory

class ClockMockSubscriber(eventBus: MessageBus, frequency: FiniteDuration) extends Actor {
  import ClockChannel.{ ClockTick, subscribeClock }

  override def preStart() = {
    subscribeClock(frequency)(eventBus)(self)
  }

  def receive = active(0)

  def active(acc: Int): Actor.Receive = {
    case _: ClockTick => context become active(acc + 1)
    case "reset" => context become active(0)
    case "get" => sender ! acc
  }
}

class ClockSuite(system: ActorSystem) extends UnitTest(system) {
  import ClockChannel.{ ClockStart, ClockStop, ClockStopAll }
  import ClockChannel.{ formatClockChildName, startClock, stopClock, unsubscribeClock }
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
    val clocks = _system.actorOf(Props(classOf[Clocks], eventBus), "clocks1")
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

    EventFilter.warning(occurrences = 1, source = clocks.path.toString).intercept({
      stopClock(frequency)(eventBus)
    })(_system)

    Await.result(gracefulStop(clocks, timeout.duration), timeout.duration)
    Await.result(gracefulStop(clock, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  "A ClockChild actor" should "produce Ticks, stop its own timer if needed and thus stop to publish Ticks" in new Bus {
    val _system = ActorSystem("ClockSuiteTest2", eventListener)

    val frequency = 25.milliseconds
    val clock = _system.actorOf(Props(classOf[ClockChild], eventBus, frequency), "clock2")
    val subscriber = _system.actorOf(Props(classOf[ClockMockSubscriber], eventBus, frequency), "subscriber2")
    val watcher = TestProbe()(_system)
    watcher.watch(clock)

    EventFilter.info(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStart("test", frequency)
    })(_system)
    
    Thread.sleep(250)
    
    EventFilter.info(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStop("test", frequency)
    })(_system)

    awaitAssert({
      watcher.expectTerminated(clock)
    }, 20.seconds)

    subscriber ! "get"
    // We assume a service quality of 90% (regarding the number of processed messages).
    expectMsgClass(classOf[Int]) should be >= (10 * 0.9).toInt
    
    Await.result(gracefulStop(clock, timeout.duration), timeout.duration)
    Await.result(gracefulStop(subscriber, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  it should "handle only one timer and stop it if there is no subscription" in new Bus {
    val _system = ActorSystem("ClockSuiteTest3", eventListener)

    val frequency = 25.milliseconds
    val clock = _system.actorOf(Props(classOf[ClockChild], eventBus, frequency), "clock3")
    val subscriber = _system.actorOf(Props(classOf[ClockMockSubscriber], eventBus, frequency), "subscriber3")
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

    Thread.sleep(250)
    EventFilter.info(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStop("test", frequency)
    })(_system)

    awaitAssert({
      watcher.expectTerminated(clock)
    }, 20.seconds)

    subscriber ! "get"
    // We assume a service quality of 90% (regarding the number of processed messages).
    expectMsgClass(classOf[Int]) should be >= (10 * 0.9).toInt

    Await.result(gracefulStop(clock, timeout.duration), timeout.duration)
    Await.result(gracefulStop(subscriber, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  "A Clocks actor" should "handle ClockChild actors and the subscribers have to receive tick messages for their frequencies" in new Bus {
    val _system = ActorSystem("ClockSuiteTest4")

    val frequency1 = 50.milliseconds
    val frequency2 = 100.milliseconds
    val frequency3 = 150.milliseconds

    val clocks = _system.actorOf(Props(classOf[Clocks], eventBus), "clocks4")

    val nbSubscribers = 100
    val subscribersF1 = scala.collection.mutable.ListBuffer[ActorRef]()
    val subscribersF2 = scala.collection.mutable.ListBuffer[ActorRef]()
    val subscribersF3 = scala.collection.mutable.ListBuffer[ActorRef]()

    for(i <- 0 until nbSubscribers) {
      subscribersF1 += _system.actorOf(Props(classOf[ClockMockSubscriber], eventBus, frequency1), s"subscriberF1-$i")
      subscribersF2 += _system.actorOf(Props(classOf[ClockMockSubscriber], eventBus, frequency2), s"subscriberF2-$i")
      subscribersF3 += _system.actorOf(Props(classOf[ClockMockSubscriber], eventBus, frequency3), s"subscriberF3-$i")
    }

    startClock(frequency1)(eventBus)
    startClock(frequency2)(eventBus)
    startClock(frequency2)(eventBus)
    startClock(frequency3)(eventBus)

    Thread.sleep(800)
    stopClock(frequency1)(eventBus)
    stopClock(frequency2)(eventBus)
    Thread.sleep(300)
    stopClock(frequency2)(eventBus)
    stopClock(frequency3)(eventBus)

    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(formatClockChildName(frequency1)).resolveOne(), timeout.duration)
      }
    }, 20.seconds)
    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(formatClockChildName(frequency2)).resolveOne(), timeout.duration)
      }
    }, 20.seconds)
    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(formatClockChildName(frequency3)).resolveOne(), timeout.duration)
      }
    }, 20.seconds)

    for(subscriber <- subscribersF1) {
      subscriber ! "get"
      // We assume a service quality of 90% (regarding the number of processed messages).
      expectMsgClass(classOf[Int]) should be >= (16 * 0.9).toInt
      subscriber ! "reset"
    }

    for(subscriber <- subscribersF2) {
      subscriber ! "get"
      // We assume a service quality of 90% (regarding the number of processed messages).
      expectMsgClass(classOf[Int]) should be >= (11 * 0.9).toInt
      subscriber ! "reset"
    }

    for(subscriber <- subscribersF3) {
      subscriber ! "get"
      // We assume a service quality of 90% (regarding the number of processed messages).
      expectMsgClass(classOf[Int]) should be >= (7 * 0.9).toInt
      subscriber ! "reset"
    }

    val testSubscriber = subscribersF1.head
    unsubscribeClock(frequency1)(eventBus)(testSubscriber)
    startClock(frequency1)(eventBus)
    Thread.sleep(250)
    stopClock(frequency1)(eventBus)

    awaitAssert({
      intercept[ActorNotFound] {
        Await.result(_system.actorSelection(formatClockChildName(frequency1)).resolveOne(), timeout.duration)
      }
    }, 20.seconds)

    testSubscriber ! "get"
    expectMsgClass(classOf[Int]) should equal (0)
    Await.result(gracefulStop(testSubscriber, timeout.duration), timeout.duration)

    for(subscriber <- (subscribersF1 - testSubscriber)) {
      subscriber ! "get"
      // We assume a service quality of 90% (regarding the number of processed messages).
      expectMsgClass(classOf[Int]) should be >= (5 * 0.9).toInt
      Await.result(gracefulStop(subscriber, timeout.duration), timeout.duration)
    }

    for(subscriber <- subscribersF2) {
      subscriber ! "get"
      expectMsgClass(classOf[Int]) should equal (0)
      Await.result(gracefulStop(subscriber, timeout.duration), timeout.duration)
    }

    for(subscriber <- subscribersF3) {
      subscriber ! "get"
      expectMsgClass(classOf[Int]) should equal (0)
      Await.result(gracefulStop(subscriber, timeout.duration), timeout.duration)
    }

    Await.result(gracefulStop(clocks, timeout.duration), timeout.duration)
    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }

  it can "handle a large number of clocks and the subscribers have to receive tick messages for their frequencies" in new Bus {
    val _system = ActorSystem("ClockSuiteTest5")

    val clocks = _system.actorOf(Props(classOf[Clocks], eventBus), "clocks5")

    val sleepingTime = 500
    val frequencies = scala.collection.mutable.ArrayBuffer[FiniteDuration]()
    val subscribers = scala.collection.mutable.ArrayBuffer[ActorRef]()

    for(i <- 10 until 50) {
      val frequency = i.milliseconds
      frequencies += frequency
      subscribers += _system.actorOf(Props(classOf[ClockMockSubscriber], eventBus, frequency), s"subscriberF$i")
    }

    for(frequency <- frequencies) {
      startClock(frequency)(eventBus)
    }

    Thread.sleep(sleepingTime)

    for(frequency <- frequencies) {
      stopClock(frequency)(eventBus)
    }

    Await.result(gracefulStop(clocks, timeout.duration), timeout.duration)

    for(subscriber <- subscribers) {
      Await.result(gracefulStop(subscriber, timeout.duration), timeout.duration)
    }

    _system.shutdown()
    _system.awaitTermination(timeout.duration)
  }
}
