/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
 */
package com.oracle.graal.jtt.micro;

import org.junit.*;

import com.oracle.graal.jtt.*;

/**
 * Tests different alignment on the stack with extended parameters (index > 5)
 */
public class BigMixedParams04 extends JTTTest {

    @SuppressWarnings("unused")
    public static long test(int choice, int i0, int i1, int i2, int i3, double d1, double d2, boolean bo1, boolean bo2, byte by, short sh, char ch, int in) {
        switch (choice) {
            case 0:
                return bo1 ? 1l : 2l;
            case 1:
                return bo2 ? 1l : 2l;
            case 2:
                return by;
            case 3:
                return sh;
            case 4:
                return ch;
            case 5:
                return in;
        }
        return 42;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0, -1, -1, -1, -1, 1d, 2d, true, false, (byte) -128, (short) -0x7FFF, (char) 0xFFFF, -0x7FFFFFF);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 1, -1, -1, -1, -1, 1d, 2d, true, false, (byte) -128, (short) -0x7FFF, (char) 0xFFFF, -0x7FFFFFF);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", 2, -1, -1, -1, -1, 1d, 2d, true, false, (byte) -128, (short) -0x7FFF, (char) 0xFFFF, -0x7FFFFFF);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", 3, -1, -1, -1, -1, 1d, 2d, true, false, (byte) -128, (short) -0x7FFF, (char) 0xFFFF, -0x7FFFFFF);
    }

    @Test
    public void run4() throws Throwable {
        runTest("test", 4, -1, -1, -1, -1, 1d, 2d, true, false, (byte) -128, (short) -0x7FFF, (char) 0xFFFF, -0x7FFFFFF);
    }

    @Test
    public void run5() throws Throwable {
        runTest("test", 5, -1, -1, -1, -1, 1d, 2d, true, false, (byte) -128, (short) -0x7FFF, (char) 0xFFFF, -0x7FFFFFF);
    }
}
