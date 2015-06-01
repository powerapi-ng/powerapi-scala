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

class ConfigurationMock(prefix: Option[String]) extends Configuration(prefix) {
  val existingKey = load { _.getString(s"${configurationPath}configuration-suite.key") }
  val wrongKey = load { _.getString(s"${configurationPath}configuration-suite.wrong-key") }
  val map = load {
    conf => {
      (for(item: Config <- JavaConversions.asScalaBuffer(conf.getConfigList(s"${configurationPath}configuration-suite.hash-map")))
        yield (item.getString("key"), item.getString("value"))).toMap
    }
  }
}

class ConfigurationSuite(system: ActorSystem) extends UnitTest(system) {

  def this() = this(ActorSystem("ConfigurationSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val simpleConfig = new ConfigurationMock(None)
  val prefixConfig1 = new ConfigurationMock(Some("prefix"))
  val prefixConfig2 = new ConfigurationMock(Some("prefix2."))

  "A Configuration class" can "be prefixed to search configuration values" in {
    simpleConfig.configurationPath should equal("")
    prefixConfig1.configurationPath should equal("prefix.")
    prefixConfig2.configurationPath should equal("prefix2.")
  }

  it should "read a value from a configuration file" in {
    simpleConfig.existingKey match {
      case ConfigValue(item) => item should equal("item")
      case _ => fail()
    }

    prefixConfig1.existingKey match {
      case ConfigValue(item) => item should equal("prefix-config1-item")
      case _ => fail()
    }

    prefixConfig2.existingKey match {
      case ConfigValue(item) => item should equal("prefix-config2-item")
      case _ => fail()
    }
  }

  it should "return the exception if the value asked does not exist" in {
    simpleConfig.wrongKey match {
      case ConfigError(ex) => ex shouldBe a [ConfigException]
      case _ => fail()
    }

    prefixConfig1.wrongKey match {
      case ConfigError(ex) => ex shouldBe a [ConfigException]
      case _ => fail()
    }

    prefixConfig2.wrongKey match {
      case ConfigError(ex) => ex shouldBe a [ConfigException]
      case _ => fail()
    }
  }

  it can "read complex values" in {
    simpleConfig.map match {
      case ConfigValue(map) => map should contain theSameElementsAs Map("item1" -> "value1", "item2" -> "value2")
      case _ => fail()
    }

    prefixConfig1.map match {
      case ConfigValue(map) => map should contain theSameElementsAs Map("prefix-config1-item1" -> "prefix-config1-value1")
      case _ => fail()
    }

    prefixConfig2.map match {
      case ConfigValue(map) => map should contain theSameElementsAs Map("prefix-config2-item2" -> "prefix-config2-value2")
      case _ => fail()
    }
  }
}
