package org.powerapi.core

import org.powerapi.UnitTesting

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{ Actor, ActorSystem, Props }
import akka.pattern.ask
import akka.util.Timeout

import akka.testkit._
import org.scalatest._

object Get
object Reset

case class SimpleReport(suid: Long, topic: String) extends Report

class Receiver(topic: String, bus: EventBus) extends Actor with ClockChannel {
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

class ClockSuite extends UnitTesting {
  import ClockChannel.{ formatTopicFromFrequency, StartClock, StopClock, OK, NOK }
  implicit val timeout = Timeout(50.milliseconds)
  implicit val system = ActorSystem("ClockTest")

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
    val receiver = TestActorRef(Props(classOf[Receiver], topic, ReportBus.eventBus))
    val clock = TestActorRef(Props(classOf[ClockChild], frequency))
    val report = SimpleReport(1, "test")

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
    val receiver = TestActorRef(Props(classOf[Receiver], topic, ReportBus.eventBus))
    val clock = TestActorRef(Props(classOf[ClockChild], frequency))
    val report = SimpleReport(1, "test")

    Await.result(clock ? StartClock(Duration.Zero, report), timeout.duration) should equal(OK)
    Await.result(clock ? StartClock(Duration.Zero, report), timeout.duration) should equal(NOK)
    Await.result(clock ? StopClock(Duration.Zero), timeout.duration) should equal(NOK)
    Thread.sleep(250)
    Await.result(receiver ? Get, timeout.duration) should (equal(25-1) or equal(25) or equal(25+1))
    Await.result(clock ? StopClock(Duration.Zero), timeout.duration) should equal(OK)
    receiver ! Reset
    Thread.sleep(100)
    Await.result(receiver ? Get, timeout.duration) should equal(0)
  }
}