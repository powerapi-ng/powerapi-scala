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
package org.powerapi.module

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import java.util.UUID
import org.powerapi.UnitTest
import org.powerapi.core.target.intToProcess

import scala.concurrent.duration.DurationInt

class CacheSuite(system: ActorSystem) extends UnitTest(system) {
  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("CacheSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "A Cache" can "be parametrized, updated" in {
    val cache = new Cache[(Double, Double)]
    val muid = UUID.randomUUID()
    val muid2 = UUID.randomUUID()
    val key = CacheKey(muid, 1)
    val key2 = CacheKey(muid2, 2)
    val key3 = CacheKey(muid2, 3)
    cache(key) = (10, 10)
    cache(key2) = (1, 1)
    cache(key3) = (2, 2)
    cache.isEmpty should equal(false)
    cache(key)(0, 0) should equal(10, 10)
    cache(key2)(0, 0) should equal(1, 1)
    cache(key3)(0, 0) should equal(2, 2)
    cache -= key
    cache(key)(0, 0) should equal(0, 0)
    cache(CacheKey(muid2, 1))(0, 0) should equal(0,0)
    cache -= muid2
    cache(key2)(0, 0) should equal(0, 0)
    cache(key3)(0, 0) should equal(0, 0)
    cache(key) = (10,10)
    cache.clear()
    cache.isEmpty should equal(true)
  }
}
