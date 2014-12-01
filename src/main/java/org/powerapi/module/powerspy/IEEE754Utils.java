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
package org.powerapi.module.powerspy;

import java.nio.ByteOrder;

public class IEEE754Utils {
  public static Float fromString(String bits) throws NumberFormatException {
    if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
      char[] bitsCharArray = bits.toCharArray();

      for (int i = 0; i < bitsCharArray.length / 2; i += 2) {
        char first = bitsCharArray[i];
        char second = bitsCharArray[i + 1];
        bitsCharArray[i] = bitsCharArray[bitsCharArray.length - i - 2];
        bitsCharArray[i + 1] = bitsCharArray[bitsCharArray.length - i - 1];
        bitsCharArray[bitsCharArray.length - i - 2] = first;
        bitsCharArray[bitsCharArray.length - i - 1] = second;
      }

      bits = String.valueOf(bitsCharArray);
    }

    return Float.intBitsToFloat(Integer.valueOf(bits, 16));
  }
}
