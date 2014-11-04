package org.powerapi.core

import org.powerapi.UnitTesting

import akka.actor.ActorSystem
import akka.testkit._
import org.scalatest._

case class MessageReport(suid: Long, topic: String) extends Report

class MessageSuite(_system: ActorSystem) extends UnitTesting(_system) {
  import ReportBus.eventBus

  def this() = this(ActorSystem("MessageSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "The ReportBus" should "handle messages by topic" in {
    val report = MessageReport(1, "topic1")
    val report2 = MessageReport(1, "topic2")

    eventBus.subscribe(testActor, "topic1")
    eventBus.publish(report)
    eventBus.publish(report2)
    expectMsg(report)
  }
}