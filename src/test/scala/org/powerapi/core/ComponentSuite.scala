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
