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

import org.powerapi.test.UnitTesting

import scala.concurrent.Await
import scala.concurrent.duration.{ Duration, DurationInt }

import akka.actor.{ Actor, ActorSystem, Props }
import akka.pattern.ask
import akka.util.Timeout
import akka.testkit.{ TestActorRef, TestKit }

case class ClockReport(suid: Long, topic: String) extends Report

object Get
object Reset

class ClockReceiver(topic: String, bus: EventBus) extends Actor with ClockChannel {
  import ClockChannel.ClockTick

  override def preStart() = {
    bus.subscribe(self, topic)
  }

  def receive = active(0)

  def active(acc: Int): Actor.Receive = {
    case msg: ClockTick => context become active(acc + 1)
    case Reset => context become active(0)
    case Get => sender ! acc
  }
}

class ClockSuite(_system: ActorSystem) extends UnitTesting(_system) {
  import ClockChannel.{ ClockAlreadyStarted, ClockStarted, ClockStillRunning, ClockStopped }
  import ClockChannel.{ clockTickTopic, StartClock, StopAllClocks, StopClock }
  implicit val timeout = Timeout(50.milliseconds)

  def this() = this(ActorSystem("ClockSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "A ClockChild actor" should "produce Ticks at a given frequency, stop its own timer if needed and thus stop to publish Ticks" in {
    val frequency = 10.milliseconds
    val topic = clockTickTopic(frequency)
    val receiver = TestActorRef(Props(classOf[ClockReceiver], topic, ReportBus.eventBus))
    val clock = TestActorRef(Props(classOf[ClockChild], frequency))
    val report = ClockReport(1, "test")

    Await.result(clock ? StartClock(Duration.Zero, report), timeout.duration) should equal(ClockStarted(frequency))
    Thread.sleep(250)
    Await.result(clock ? StopClock(Duration.Zero), timeout.duration) should equal(ClockStopped(frequency))
    val test = Await.result(receiver ? Get, timeout.duration).asInstanceOf[Int] should be (25 +- 5)
    receiver ! Reset
    Thread.sleep(100)
    Await.result(receiver ? Get, timeout.duration).asInstanceOf[Int] should equal(0)
  }

  it should "handle only one timer and stop it if there is no subscription" in {
    val frequency = 10.milliseconds
    val topic = clockTickTopic(frequency)
    val receiver = TestActorRef(Props(classOf[ClockReceiver], topic, ReportBus.eventBus))
    val clock = TestActorRef(Props(classOf[ClockChild], frequency))
    val report = ClockReport(1, "test")

    Await.result(clock ? StartClock(Duration.Zero, report), timeout.duration) should equal(ClockStarted(frequency))
    Await.result(clock ? StartClock(Duration.Zero, report), timeout.duration) should equal(ClockAlreadyStarted(frequency))
    Await.result(clock ? StopClock(Duration.Zero), timeout.duration) should equal(ClockStillRunning(frequency))
    Thread.sleep(250)
    Await.result(clock ? StopClock(Duration.Zero), timeout.duration) should equal(ClockStopped(frequency))
    Await.result(receiver ? Get, timeout.duration).asInstanceOf[Int] should be (25 +- 5)
    receiver ! Reset
    Thread.sleep(100)
    Await.result(receiver ? Get, timeout.duration).asInstanceOf[Int] should equal(0)
  }

  "A Clock actor" should "handle ClockChild actors" in {
    val frequency1 = 10.milliseconds
    val topic1 = clockTickTopic(frequency1)
    val frequency2 = 50.milliseconds
    val topic2 = clockTickTopic(frequency2)
    val frequency3 = 25.milliseconds
    val topic3 = clockTickTopic(frequency3)

    val clockTimeout = Timeout(100.milliseconds)

    val receiver1 = TestActorRef(Props(classOf[ClockReceiver], topic1, ReportBus.eventBus))
    val receiver2 = TestActorRef(Props(classOf[ClockReceiver], topic2, ReportBus.eventBus))
    val receiver3 = TestActorRef(Props(classOf[ClockReceiver], topic3, ReportBus.eventBus))

    val clock = TestActorRef(Props(classOf[Clock], clockTimeout))
    val report = ClockReport(1, "test")

    clock ! StartClock(frequency1, report)
    clock ! StartClock(frequency2, report)
    clock ! StartClock(frequency2, report)
    clock ! StartClock(frequency3, report)
    clock ! StartClock(frequency3, report)

    Thread.sleep(250)
    clock ! StopClock(frequency1)
    clock ! StopClock(frequency2)
    clock ! StartClock(frequency2, report)
    Thread.sleep(150)
    clock ! StopAllClocks
    
    Thread.sleep(100)

    Await.result(receiver1 ? Get, timeout.duration).asInstanceOf[Int] should be (25 +- 5)
    Await.result(receiver2 ? Get, timeout.duration).asInstanceOf[Int] should be (8 +- 5)
    Await.result(receiver3 ? Get, timeout.duration).asInstanceOf[Int] should be (16 +- 5)
  }
}
