package org.powerapi.core

import org.powerapi.UnitTesting

import akka.actor.ActorSystem
import akka.testkit._
import org.scalatest._

object Expected
object NonExpected
object OK

class TestComponent extends Component {
  def acquire = {
    case Expected => sender ! OK
  }
}

class ComponentSuite(_system: ActorSystem) extends UnitTesting(_system) {
  def this() = this(ActorSystem("ComponentSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "A component" should "have a default and a processing behavior" in {
    val component = TestActorRef[TestComponent]
    component ! Expected
    expectMsg(OK)
    intercept[UnsupportedOperationException] { component.receive(NonExpected) }
  }
}