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

import org.powerapi.UnitTest

class MessageBusSuite extends UnitTest {

  override def afterAll() = {
    system.terminate()
  }

  "A MessageBus" should "handle messages by topic" in {
    val eventBus = new MessageBus
    val msg1 = new Message {
      val topic = "topic1"
    }
    val msg2 = new Message {
      val topic = "topic2"
    }

    eventBus.subscribe(testActor, "topic1")
    eventBus.publish(msg1)
    eventBus.publish(msg2)
    expectMsg(msg1)
    eventBus.unsubscribe(testActor)
    eventBus.publish(msg2)
    expectNoMsg()
  }
}
