/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2015 Inria, University of Lille 1.
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
package org.powerapi.code

import akka.actor.{Props, Actor}
import akka.routing.RoundRobinPool
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import org.powerapi.core.OSHelper
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class ConvertAddress(binaryFilePath: String, address: String)

/**
 * This actor acts as a router and it allows to convert hexadecimal address to a function name
 * with the help of the OSHelper (works currently on linux).
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class AddressResolver(osHelper: OSHelper, timeout: Timeout, nbRoutees: Int) extends Actor {
  val router = context.actorOf(RoundRobinPool(nbRoutees).props(Props(classOf[AddressResolverRoutee], osHelper)))

  def receive: Actor.Receive = running(Map())

  def running(converted: Map[String, Future[String]]): Actor.Receive = {
    case msg: ConvertAddress => {
      if(!converted.contains(msg.address)) {
        val name = router.ask(msg)(timeout).asInstanceOf[Future[String]]
        pipe(name) to sender
        context.become(running(converted + (msg.address -> name)))
      }

      else pipe(converted(msg.address)) to sender
    }
  }
}

class AddressResolverRoutee(osHelper: OSHelper) extends Actor {
  def receive: Actor.Receive = {
    case ConvertAddress(binaryFilePath, address) => sender ! osHelper.getFunctionNameByAddress(binaryFilePath, address)
  }
}
