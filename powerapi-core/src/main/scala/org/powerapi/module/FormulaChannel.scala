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

import java.util.UUID

import akka.actor.ActorRef

import org.powerapi.core.target.Target
import org.powerapi.core.{Channel, Message, MessageBus}

/**
  * Base channel for the Formula components.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
object FormulaChannel extends Channel {

  type M = FormulaMessage
  private val topic = "formula:handling"

  /**
    * Used to subscribe to FormulaMessage on the right topic.
    */
  def subscribeFormulaChannel: MessageBus => ActorRef => Unit = {
    subscribe(topic)
  }

  /**
    * Used to interact with the Supervisor.
    */
  def startFormula(muid: UUID, target: Target, claz: Class[_ <: Formula], args: Seq[Any]): MessageBus => Unit = {
    publish(FormulaStart(topic, muid, target, claz, args))
  }

  def stopFormula(muid: UUID): MessageBus => Unit = {
    publish(FormulaStop(topic, muid))
  }

  def stopAllFormula: MessageBus => Unit = {
    publish(FormulaStopAll(topic))
  }

  /**
    * Used to format the Formula actor name.
    */
  def formatFormulaName(claz: Class[_ <: Formula], muid: UUID, target: Target): String = {
    s"_${claz.getSimpleName.toLowerCase}_${muid}_${target}_"
  }

  trait FormulaMessage extends Message

  case class FormulaStart(topic: String, muid: UUID, target: Target, claz: Class[_ <: Formula], args: Seq[Any]) extends FormulaMessage

  case class FormulaStop(topic: String, muid: UUID) extends FormulaMessage

  case class FormulaStopAll(topic: String) extends FormulaMessage

}
