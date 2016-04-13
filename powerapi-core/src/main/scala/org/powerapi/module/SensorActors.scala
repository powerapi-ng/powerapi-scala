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

import akka.actor.SupervisorStrategy.{Directive, Resume}
import akka.actor.{Actor, PoisonPill, Props}

import org.powerapi.core.target.Target
import org.powerapi.core.{APIComponent, MessageBus, Supervisor}
import org.powerapi.module.SensorChannel.{SensorStart, SensorStop, SensorStopAll, formatSensorName, subscribeSensorChannel}

/**
  * Abstract class to extend for each specific sensor component.
  * One Sensor is created per muid/target and allows to handle common messages.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
abstract class Sensor(eventBus: MessageBus, muid: UUID, target: Target) extends APIComponent {

  def receive: Actor.Receive = starting orElse default

  def starting: Actor.Receive = {
    case msg: SensorStart if msg.muid == muid && msg.target == target => start()
  }

  /**
    * Start the sensor by using the init() method.
    */
  def start(): Unit = {
    init()
    log.info("sensor is started, class: {}, muid: {}, target: {}", getClass.getName, muid, target)
    context.become(handler orElse sensorDefault)
  }

  def sensorDefault: Actor.Receive = running orElse default

  def running: Actor.Receive = {
    case msg: SensorStop if msg.muid == muid => stop()
    case _: SensorStopAll => stop()
  }

  /**
    * Stop the Sensor by using the terminate() method and kill the actor itself.
    */
  def stop(): Unit = {
    terminate()
    log.info("sensor is stopped, class: {}, muid: {}, target: {}", getClass.getName, muid, target)
    self ! PoisonPill
  }

  /**
    * These two methods are used to activate/deactivate specific Sensor behaviors, such as subscribe/unsubscribe to the
    * right topics.
    */
  def init(): Unit

  def terminate(): Unit

  /**
    * Handle specific messages of each Formula implementation.
    */
  def handler: Actor.Receive
}

/**
  * This Sensor supervisor listens the bus and reacts on the received messages.
  * It is responsible to handle a pool of sensors for the components attached to a PowerMeter.
  *
  * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
  */
class Sensors(eventBus: MessageBus) extends Supervisor {

  override def preStart(): Unit = {
    subscribeSensorChannel(eventBus)(self)
    super.preStart()
  }

  /**
    * Sensor actors can only launch exception if the message received is not handled.
    */
  def handleFailure: PartialFunction[Throwable, Directive] = {
    case _: UnsupportedOperationException => Resume
  }

  def receive: Actor.Receive = running orElse default

  def running: Actor.Receive = {
    case msg: SensorStart => start(msg)
    case msg: SensorStop => stop(msg)
    case msg: SensorStopAll => stopAll(msg)
  }

  /**
    * Start a new Sensor for a given class implementation, a muid and a target.
    *
    * @param msg Message received for starting a Sensor.
    */
  def start(msg: SensorStart): Unit = {
    val name = formatSensorName(msg.claz, msg.muid, msg.target)

    val child = context.child(name) match {
      case None =>
        val child = context.actorOf(Props(msg.claz, msg.args: _*), name)
        child ! msg
      case _ =>
        log.warning("this sensor is started already, class: {}, muid, {}, target: {}", msg.claz.getName, msg.muid, msg.target)
    }
  }

  /**
    * Stop all sensors associated to a given muid.
    *
    * @param msg Message received for stopping sensors created for a given muid.
    */
  def stop(msg: SensorStop): Unit = {
    context.actorSelection(s"*${msg.muid}*") ! msg
  }

  /**
    * Stop all sensors.
    *
    * @param msg Message received for stopping all sensors.
    */
  def stopAll(msg: SensorStopAll): Unit = {
    context.actorSelection("*") ! msg
  }
}
