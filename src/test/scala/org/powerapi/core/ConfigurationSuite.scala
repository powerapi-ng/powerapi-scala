/*
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
 *
 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */
package org.powerapi.core

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.{Config, ConfigException}
import org.powerapi.UnitTest
import scala.collection.JavaConversions

class ConfigurationMock extends Configuration {
  val existingKey = load { _.getString("configuration-suite.key") }
  val wrongKey = load { _.getString("configuration-suite.wrong-key") }
  val map = load {
    conf => {
      (for(item: Config <- JavaConversions.asScalaBuffer(conf.getConfigList("configuration-suite.hash-map")))
        yield (item.getString("key"), item.getString("value"))).toMap
    }
  }
}

class ConfigurationSuite(system: ActorSystem) extends UnitTest(system) {

  def this() = this(ActorSystem("ConfigurationSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val config = new ConfigurationMock

  "A ConfigurationMock class" should "read a value from a configuration file" in {
    config.existingKey match {
      case ConfigValue(item) => item should equal("item")
      case _ => fail()
    }
  }

  it should "return the exception if the value asked does not exist" in {
    config.wrongKey match {
      case ConfigError(ex) => ex shouldBe a [ConfigException]
      case _ => fail()
    }
  }

  it can "read complex values" in {
    config.map match {
      case ConfigValue(map) => map should contain theSameElementsAs Map("item1" -> "value1", "item2" -> "value2")
      case _ => fail()
    }
  }
}
