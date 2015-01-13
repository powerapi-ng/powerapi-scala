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

/**
 * Targets are system elements that can be monitored by PowerAPI
 *
 * @author Romain Rouvoy <romain.rouvoy@univ-lille1.fr>
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
trait Target

/**
 * Monitoring target for a specific Process IDentifier.
 *
 * @param pid: process identifier.
 *
 * @author Romain Rouvoy <romain.rouvoy@univ-lille1.fr>
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
case class Process(pid: Int) extends Target

/**
 * Monitoring target for a specific application.
 *
 * @param name: name of the application.
 *
 * @author Romain Rouvoy <romain.rouvoy@univ-lille1.fr>
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
case class Application(name: String) extends Target

/**
 * Target usage ratio.
 *
 * @param ratio: usage ratio.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
case class TargetUsageRatio(ratio: Double)

/**
 * Monitoring target for the whole system.
 *
 * @author Romain Rouvoy <romain.rouvoy@univ-lille1.fr>
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
object All extends Target
