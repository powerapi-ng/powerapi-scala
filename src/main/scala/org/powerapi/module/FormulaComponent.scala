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
package org.powerapi.module

import akka.event.LoggingReceive
import org.powerapi.core.{APIComponent, MessageBus}
import SensorChannel.SensorReport
import scala.reflect.ClassTag

/**
 * Base trait for each PowerAPI formula.
 * Each of them should react to a SensorReport, compute the power and then publish a PowerReport.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
abstract class FormulaComponent[SR <: SensorReport : ClassTag](eventBus: MessageBus) extends APIComponent {

  override def preStart(): Unit = {
    subscribeSensorReport()
    super.preStart()
  }

  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    // To avoid the abstract type pattern eliminated by erasure.
    case msg: SR if implicitly[ClassTag[SR]].runtimeClass.isInstance(msg) => compute(msg)
  } orElse default

  def subscribeSensorReport(): Unit
  def compute(sensorReport: SR): Unit
}
