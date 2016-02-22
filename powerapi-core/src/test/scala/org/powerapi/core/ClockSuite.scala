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

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}

import akka.actor.{Props, Terminated}
import akka.pattern.gracefulStop
import akka.testkit.{EventFilter, TestActorRef, TestProbe}
import akka.util.Timeout

import org.powerapi.UnitTest
import org.powerapi.core.ClockChannel.{ClockStart, ClockStop, ClockTick, startClock, stopAllClock, stopClock, subscribeClockTick, unsubscribeClockTick}

class ClockSuite extends UnitTest {

  val timeout = Timeout(1.seconds)
  val threshold = 0.5

  override def afterAll() {
    system.shutdown()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  "A ClockChild actor" should "launch an exception when the messages received cannot be handled" in new Bus {
    val frequency = 50.milliseconds
    val clock = TestActorRef(Props(classOf[ClockChild], eventBus, frequency), "clock")

    EventFilter.warning(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStop("test", frequency)
    })

    EventFilter.warning(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStart("test", Duration.Zero)
    })

    // Not an exception, just an assertion (switching in the running state).
    EventFilter.info(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStart("test", frequency)
    })

    EventFilter.warning(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStart("test", Duration.Zero)
    })

    EventFilter.warning(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStop("test", Duration.Zero)
    })

    EventFilter.info(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStop("test", frequency)
    })

    Await.result(gracefulStop(clock, timeout.duration), timeout.duration)
  }

  it should "produce Ticks, stop its own timer if needed and thus stop to publish Ticks" in new Bus {
    val frequency = 25.milliseconds
    var messages = Seq[ClockTick]()

    val clock = TestActorRef(Props(classOf[ClockChild], eventBus, frequency), "clock")

    val watcher = TestProbe()
    watcher.watch(clock)

    EventFilter.info(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStart("test", frequency)
    })

    subscribeClockTick(frequency)(eventBus)(testActor)

    receiveWhile(10.seconds, messages = ((10.seconds / frequency) * threshold).toInt) {
      case msg: ClockTick => msg
    }.size should equal(((10.seconds / frequency) * threshold).toInt)

    EventFilter.info(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStop("test", frequency)
    })

    watcher.expectTerminated(clock)
    watcher.expectNoMsg()

    Await.result(gracefulStop(clock, timeout.duration), timeout.duration)
  }

  it should "handle only one timer and stop it if there is no subscription" in new Bus {
    val frequency = 25.milliseconds
    var messages = Seq[ClockTick]()

    val clock = TestActorRef(Props(classOf[ClockChild], eventBus, frequency), "clock")

    val watcher = TestProbe()
    watcher.watch(clock)

    EventFilter.info(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStart("test", frequency)
    })

    EventFilter.info(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStart("test", frequency)
    })

    EventFilter.info(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStop("test", frequency)
    })

    subscribeClockTick(frequency)(eventBus)(testActor)

    receiveWhile(10.seconds, messages = ((10.seconds / frequency) * threshold).toInt) {
      case msg: ClockTick => msg
    }.size should equal(((10.seconds / frequency) * threshold).toInt)

    EventFilter.info(occurrences = 1, source = clock.path.toString).intercept({
      clock ! ClockStop("test", frequency)
    })

    watcher.expectTerminated(clock)
    watcher.expectNoMsg()

    Await.result(gracefulStop(clock, timeout.duration), timeout.duration)
  }

  "A Clocks actor" should "handle ClockChild actors" in new Bus {
    val frequency1 = 50.milliseconds
    val frequency2 = 100.milliseconds
    val frequency3 = 150.milliseconds

    val clocks = TestActorRef(Props(classOf[Clocks], eventBus), "clocks")
    val watcher = TestProbe()

    EventFilter.info(start = "clock started", occurrences = 3).intercept({
      startClock(frequency1)(eventBus)
      startClock(frequency2)(eventBus)
      startClock(frequency3)(eventBus)
    })

    subscribeClockTick(frequency1)(eventBus)(testActor)
    receiveWhile(10.seconds, messages = ((10.seconds / frequency1) * threshold).toInt) {
      case msg: ClockTick => msg
    }.size should equal(((10.seconds / frequency1) * threshold).toInt)
    unsubscribeClockTick(frequency1)(eventBus)(testActor)
    expectNoMsg()

    subscribeClockTick(frequency2)(eventBus)(testActor)
    receiveWhile(10.seconds, messages = ((10.seconds / frequency2) * threshold).toInt) {
      case msg: ClockTick => msg
    }.size should equal(((10.seconds / frequency2) * threshold).toInt)
    unsubscribeClockTick(frequency2)(eventBus)(testActor)
    expectNoMsg()

    subscribeClockTick(frequency3)(eventBus)(testActor)
    receiveWhile(10.seconds, messages = ((10.seconds / frequency3) * threshold).toInt) {
      case msg: ClockTick => msg
    }.size should equal(((10.seconds / frequency3) * threshold).toInt)
    unsubscribeClockTick(frequency3)(eventBus)(testActor)
    expectNoMsg()

    val children = clocks.children.seq
    children.foreach(child => watcher.watch(child))

    stopClock(frequency1)(eventBus)
    stopAllClock(eventBus)

    watcher.receiveN(3).asInstanceOf[Seq[Terminated]].map(_.actor) should contain theSameElementsAs children
    watcher.expectNoMsg()

    Await.result(gracefulStop(clocks, timeout.duration), timeout.duration)
  }
}
