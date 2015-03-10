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
import org.powerapi.core.target.Target

/**
 * Cache entry.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
case class CacheKey(muid: UUID, target: Target)

/**
 * Delegate class used for caching data.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
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

  def clear(): Unit = {
    cache.clear()
  }

  def isEmpty: Boolean = {
    cache.isEmpty
  }

  def -=(key: CacheKey): Unit = {
    cache -= key
  }

  def -=(muid: UUID): Unit = {
    cache.filter(entry => entry._1.muid == muid).foreach(entry => cache -= entry._1)
  }
}
