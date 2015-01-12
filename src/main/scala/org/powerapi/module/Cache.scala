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

import java.util.UUID
import org.powerapi.core.Target

/**
 * Cache entry.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
case class CacheKey(muid: UUID, target: Target)

/**
 * Delegate class used for caching data.
 *
 * @author Maxime Colmant <maxime.colmant@gmail.com>
 */
class Cache[T] {
  /**
   * Internal cache
   */
  private lazy val cache = collection.mutable.Map[CacheKey, T]()

  def apply(key: CacheKey)(default: T): T = {
    cache.getOrElse(key, default)
  }

  def update(key: CacheKey, now: T): Unit = {
    val old = cache.getOrElse(key, now)
    cache += (key -> now)
  }
}
