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
package org.powerapi.core.target

/**
 * Targets are system elements that can be monitored by PowerAPI
 *
 * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
trait Target

/**
 * Monitoring target for a specific Process IDentifier.
 *
 * @param pid: process identifier.
 *
 * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
case class Process(pid: Int) extends Target {
  override def toString: String = s"$pid"
}

/**
 * Monitoring target for a specific application.
 *
 * @param name: name of the application.
 *
 * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
case class Application(name: String) extends Target {
  override def toString: String = name
}

/**
 * Monitoring targets for a specific container.
 *
 * @param name: id of the container.
 *
 * @author <a href="mailto:l.huertas.pro@gmail.com">Loïc Huertas</a>
 */
case class Container(id: String) extends Target {
  override def toString: String = id
}

/**
 * Target usage ratio.
 *
 * @param ratio: usage ratio.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
case class TargetUsageRatio(ratio: Double)

/**
 * Monitoring target for the whole system.
 *
 * @author <a href="mailto:romain.rouvoy@univ-lille1.fr">Romain Rouvoy</a>
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
object All extends Target {
  override def toString = "All"
}
