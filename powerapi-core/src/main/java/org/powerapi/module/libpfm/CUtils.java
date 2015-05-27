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
package org.powerapi.module.libpfm;

import org.bridj.ann.Library;
import org.bridj.ann.Runtime;
import org.bridj.ann.CLong;
import org.bridj.BridJ;
import org.bridj.CRuntime;
import org.bridj.Pointer;
import perfmon2.libpfm.perf_event_attr;

/**
 * This class is used for binding several functions available on the system or in C.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
@Library("c")
@Runtime(CRuntime.class)
public class CUtils {
  static {
    BridJ.register();
  }

  /**
   * perf_event_open maccro (not generated correctly).
   */
  public static int perf_event_open(int __nrPerfEventOpen, Pointer<perf_event_attr> __hw, int __pid, int __cpu, int __gr, @CLong long __flags) {
    return syscall(__nrPerfEventOpen, Pointer.getPeer(__hw), __pid, __cpu, __gr, __flags);
  }
  private native static int syscall(int __code, Object... varArgs1);

  /**
   * Interact with a given file descriptor. In this case, we use it to enable, disable and reset a file descriptor (so, a counter).
   */
  public static native int ioctl(int __fd, @CLong long __request, Object... varArgs1);

  /**
   * Allow to read values from a file descriptor.
   */
  public static long read(int __fd, Pointer<? > __buf, @CLong long __nbytes) {
    return read(__fd, Pointer.getPeer(__buf), __nbytes);
  }
  private native static long read(int __fd, @CLong long __buf, @CLong long __nbytes);

  /**
   * Close a file descriptor
   */
  public static native int close(int __fd);
}
