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
import scala.concurrent.duration.{ Duration, DurationInt, FiniteDuration, MILLISECONDS }

import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.pattern.ask
import akka.util.Timeout
import akka.testkit.{ EventFilter, TestActorRef, TestKit }

import com.typesafe.config.ConfigFactory

class ClockMockSubscriber(frequency: FiniteDuration) extends Actor {
  import ClockChannel.{ ClockTick, subscribeClock }

  override def preStart() = {
    subscribeClock(frequency)(self)
  }

  def receive = active(0)

  def active(acc: Int): Actor.Receive = {
    case _: ClockTick => context become active(acc + 1)
    case "reset" => context become active(0)
    case "get" => sender ! acc
  }
}

class ClockSuite(_system: ActorSystem) extends UnitTest(_system) {
  import ClockChannel.{ ClockAlreadyStarted, ClockStart, ClockStarted, ClockStillRunning, ClockStop }
  import ClockChannel.{ ClockStopAll, ClockStopped, startClock, stopAllClock, stopClock, unsubscribeClock }
  implicit val timeout = Timeout(50.milliseconds)

  def this() = this(ActorSystem("ClockSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(_system)
  }

  "A ClockChild actor" should "produce Ticks at a given frequency, stop its own timer if needed and thus stop to publish Ticks" in {
    implicit val system = ActorSystem("ClockSuite")
    val frequency = 25.milliseconds

    val subscriber = TestActorRef(Props(classOf[ClockMockSubscriber], frequency))(system)
    val clock = TestActorRef(Props(classOf[ClockChild], frequency))(system)

    clock ! ClockStart("test", frequency)
    expectMsg(ClockStarted(frequency))
    
    Thread.sleep(250)
    clock ! ClockStop("test", frequency)
    expectMsg(ClockStopped(frequency))
    subscriber ! "get"
    expectMsgClass(classOf[Int]) should be (10 +- 5)

    subscriber ! "reset"
    Thread.sleep(100)
    subscriber ! "get"
    expectMsgClass(classOf[Int]) should equal(0)

    TestKit.shutdownActorSystem(system)
  }

  it should "handle only one timer and stop it if there is no subscription" in {
    implicit val system = ActorSystem("ClockSuite")

    val frequency = 25.milliseconds
    val subscriber = TestActorRef(Props(classOf[ClockMockSubscriber], frequency))(system)
    val clock = TestActorRef(Props(classOf[ClockChild], frequency))(system).asInstanceOf[TestActorRef[ClockChild]]

    clock ! ClockStart("test", frequency)
    expectMsg(ClockStarted(frequency))

    clock ! ClockStart("test", frequency)
    expectMsg(ClockAlreadyStarted(frequency))

    clock ! ClockStop("test", frequency)
    expectMsg(ClockStillRunning(frequency))

    Thread.sleep(250)
    clock ! ClockStop("test", frequency)
    expectMsg(ClockStopped(frequency))
    subscriber ! "get"
    expectMsgClass(classOf[Int]) should be (10 +- 2)

    subscriber ! "reset"
    Thread.sleep(500)
    subscriber ! "get"
    expectMsgClass(classOf[Int]) should equal(0)

    TestKit.shutdownActorSystem(system)
  }

  it should "launch an exception when the messages received cannot handled" in {
    implicit val system = ActorSystem("ClockSuite")

    val frequency = 25.milliseconds
    val subscriber = TestActorRef(Props(classOf[ClockMockSubscriber], frequency))(system)
    val clock = TestActorRef(Props(classOf[ClockChild], frequency))(system).asInstanceOf[TestActorRef[ClockChild]]

    EventFilter.warning(occurrences = 1) intercept {
      clock ! ClockStop("test", frequency)
    }

    EventFilter.warning(occurrences = 1) intercept {
      clock ! ClockStart("test", Duration.Zero)
    }

    clock ! ClockStart("test", frequency)
    expectMsg(ClockStarted(frequency))

    EventFilter.warning(occurrences = 1) intercept {
      clock ! ClockStart("test", Duration.Zero)
    }

    EventFilter.warning(occurrences = 1) intercept {
      clock ! ClockStop("test", Duration.Zero)
    }

    clock ! ClockStop("test", frequency)
    expectMsg(ClockStopped(frequency))

    TestKit.shutdownActorSystem(system)
  }

  "A Clock actor" should "handle ClockChild actors and the subscribers have to receive tick messages for their frequencies" in {
    implicit val system = ActorSystem("ClockSuite")

    val frequency1 = 25.milliseconds
    val frequency2 = 50.milliseconds
    val frequency3 = 100.milliseconds

    val clockTimeout = Timeout(1.seconds)
    val clock = TestActorRef(Props(classOf[Clock], clockTimeout))(system)

    val nbSubscribers = 100
    val subscribersF1 = scala.collection.mutable.ListBuffer[TestActorRef[ClockMockSubscriber]]()
    val subscribersF2 = scala.collection.mutable.ListBuffer[TestActorRef[ClockMockSubscriber]]()
    val subscribersF3 = scala.collection.mutable.ListBuffer[TestActorRef[ClockMockSubscriber]]()

    for(i <- 0 until nbSubscribers) {
      subscribersF1 += TestActorRef(Props(classOf[ClockMockSubscriber], frequency1))(system)
      subscribersF2 += TestActorRef(Props(classOf[ClockMockSubscriber], frequency2))(system)
      subscribersF3 += TestActorRef(Props(classOf[ClockMockSubscriber], frequency3))(system)
    }

    startClock(frequency1)
    startClock(frequency2)
    startClock(frequency2)
    startClock(frequency3)
    startClock(frequency3)

    Thread.sleep(500)
    stopClock(frequency1)
    stopClock(frequency2)
    startClock(frequency2)
    Thread.sleep(300)
    stopAllClock()
    
    Thread.sleep(200)

    for(subscriber <- subscribersF1) {
      subscriber ! "get"
      expectMsgClass(classOf[Int]) should be (20 +- 5)
      subscriber ! "reset"
    }
    for(subscriber <- subscribersF2) {
      subscriber ! "get"
      expectMsgClass(classOf[Int]) should be (16 +- 5)
      subscriber ! "reset"
    }
    for(subscriber <- subscribersF3) {
      subscriber ! "get"
      expectMsgClass(classOf[Int]) should be (8 +- 5)
      subscriber ! "reset"
    }

    val testSubscriber = subscribersF1.head
    unsubscribeClock(frequency1)(testSubscriber)
    startClock(frequency1)
    Thread.sleep(300)
    stopClock(frequency1)

    Thread.sleep(100)

    testSubscriber ! "get"
    expectMsgClass(classOf[Int]) should equal (0)

    for(subscriber <- (subscribersF1 - testSubscriber)) {
      subscriber ! "get"
      expectMsgClass(classOf[Int]) should be (16 +- 5)
    }

    for(subscriber <- subscribersF2) {
      subscriber ! "get"
      expectMsgClass(classOf[Int]) should equal (0)
    }

    for(subscriber <- subscribersF3) {
      subscriber ! "get"
      expectMsgClass(classOf[Int]) should equal (0)
    }

    TestKit.shutdownActorSystem(system)
  }

  it can "handle a large number of clocks and the subscribers have to receive tick messages for their frequencies" in {
    implicit val system = ActorSystem("ClockSuite")

    val clockTimeout = Timeout(1.seconds)
    val clock = TestActorRef(Props(classOf[Clock], clockTimeout))(system)

    val sleepingTime = 500
    val frequencies = scala.collection.mutable.ArrayBuffer[FiniteDuration]()
    val subscribers = scala.collection.mutable.ArrayBuffer[TestActorRef[ClockMockSubscriber]]()

    for(i <- 50 to 100) {
      val frequency = FiniteDuration(i, MILLISECONDS)
      frequencies += frequency
      subscribers += TestActorRef(Props(classOf[ClockMockSubscriber], frequency))
    }

    for(frequency <- frequencies) {
      startClock(frequency)
    }

    Thread.sleep(sleepingTime)

    for(frequency <- frequencies) {
      stopClock(frequency)
    }

    for(i <- 0 until frequencies.size) {
      val frequency = frequencies(i)
      val subscriber = subscribers(i)
      val minNbExpectedMsg = (sleepingTime / frequency.toMillis).toInt
      
      subscriber ! "get"
      expectMsgClass(classOf[Int]) should be >= minNbExpectedMsg
    }

    TestKit.shutdownActorSystem(system)
  }

  it should "launch an exception when the messages received cannot handled" in {
    implicit val system = ActorSystem("ClockSuite")

    val clockTimeout = Timeout(1.seconds)
    val clock = TestActorRef(Props(classOf[Clock], clockTimeout))(system)
    val frequency = 25.milliseconds

    EventFilter.warning(occurrences = 1) intercept {
      stopClock(frequency)
    }

    TestKit.shutdownActorSystem(system)
  }
}
