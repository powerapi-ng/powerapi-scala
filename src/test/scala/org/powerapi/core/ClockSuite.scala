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

import org.powerapi.UnitTesting

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{ Actor, ActorSystem, Props }
import akka.pattern.ask
import akka.util.Timeout
import akka.testkit._
import org.scalatest._

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
  import ClockChannel.{ formatTopicFromFrequency, StartClock, StopAllClocks, StopClock, OK, NOK }
  implicit val timeout = Timeout(50.milliseconds)

  def this() = this(ActorSystem("ClockSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "ClockChannel.formatTopicFromFrequency" should "format correctly a duration to a topic" in {
    val day = 1.days
    val hour = 1.hours
    val microsecond = 1.microseconds
    val millisecond = 1.milliseconds
    val minute = 1.minutes
    val nanosecond = 1.nanoseconds
    val second = 1.seconds

    formatTopicFromFrequency(day) should equal("tick:" + day.toNanos.toString)
    formatTopicFromFrequency(hour) should equal("tick:" + hour.toNanos.toString)
    formatTopicFromFrequency(microsecond) should equal("tick:" + microsecond.toNanos.toString)
    formatTopicFromFrequency(millisecond) should equal("tick:" + millisecond.toNanos.toString)
    formatTopicFromFrequency(minute) should equal("tick:" + minute.toNanos.toString)
    formatTopicFromFrequency(nanosecond) should equal("tick:" + nanosecond.toNanos.toString)
    formatTopicFromFrequency(second) should equal("tick:" + second.toNanos.toString)
  }

  "A ClockChild actor" should "produce Ticks at a given frequency, stop its own timer if needed and thus stop to publish Ticks" in {
    val frequency = 10.milliseconds
    val topic = formatTopicFromFrequency(frequency)
    val receiver = TestActorRef(Props(classOf[ClockReceiver], topic, ReportBus.eventBus))
    val clock = TestActorRef(Props(classOf[ClockChild], frequency))
    val report = ClockReport(1, "test")

    Await.result(clock ? StartClock(Duration.Zero, report), timeout.duration) should equal(OK)
    Thread.sleep(250)
    Await.result(receiver ? Get, timeout.duration) should (equal(25-1) or equal(25) or equal(25+1))
    Await.result(clock ? StopClock(Duration.Zero), timeout.duration) should equal(OK)
    receiver ! Reset
    Thread.sleep(100)
    Await.result(receiver ? Get, timeout.duration) should equal(0)
  }

  it should "handle only one timer and stop it if there is not a subscription which uses it" in {
    val frequency = 10.milliseconds
    val topic = formatTopicFromFrequency(frequency)
    val receiver = TestActorRef(Props(classOf[ClockReceiver], topic, ReportBus.eventBus))
    val clock = TestActorRef(Props(classOf[ClockChild], frequency))
    val report = ClockReport(1, "test")

    Await.result(clock ? StartClock(Duration.Zero, report), timeout.duration) should equal(OK)
    Await.result(clock ? StartClock(Duration.Zero, report), timeout.duration) should equal(NOK)
    Await.result(clock ? StopClock(Duration.Zero), timeout.duration) should equal(OK)
    Thread.sleep(250)
    Await.result(receiver ? Get, timeout.duration) should (equal(25-1) or equal(25) or equal(25+1))
    Await.result(clock ? StopClock(Duration.Zero), timeout.duration) should equal(OK)
    receiver ! Reset
    Thread.sleep(100)
    Await.result(receiver ? Get, timeout.duration) should equal(0)
  }

  "A Clock actor" should "handle ClockChild actors" in {
    val frequency1 = 10.milliseconds
    val topic1 = formatTopicFromFrequency(frequency1)
    val frequency2 = 50.milliseconds
    val topic2 = formatTopicFromFrequency(frequency2)
    val frequency3 = 25.milliseconds
    val topic3 = formatTopicFromFrequency(frequency3)

    val clockTimeout = Timeout(100.milliseconds)

    val receiver1 = TestActorRef(Props(classOf[ClockReceiver], topic1, ReportBus.eventBus))
    val receiver2 = TestActorRef(Props(classOf[ClockReceiver], topic2, ReportBus.eventBus))
    val receiver3 = TestActorRef(Props(classOf[ClockReceiver], topic3, ReportBus.eventBus))

    val clock = TestActorRef(Props(classOf[Clock], clockTimeout))
    val report = ClockReport(1, "test")

    clock ! StartClock(frequency1, report)
    clock ! StartClock(frequency2, report)
    clock ! StartClock(frequency3, report)
    clock ! StartClock(frequency3, report)

    Thread.sleep(250)
    clock ! StopClock(frequency1)
    Thread.sleep(50)
    clock ! StopClock(frequency2)
    Thread.sleep(100)
    clock ! StopAllClocks
    
    Thread.sleep(100)

    Await.result(receiver1 ? Get, timeout.duration) should (equal(25-1) or equal(25) or equal(25+1))
    Await.result(receiver2 ? Get, timeout.duration) should (equal(6-1) or equal(6) or equal(6+1))
    Await.result(receiver3 ? Get, timeout.duration) should (equal(16-1) or equal(16) or equal(16+1))
  }
}