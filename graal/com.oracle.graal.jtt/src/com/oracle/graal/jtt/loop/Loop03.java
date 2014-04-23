/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.jtt.loop;

import org.junit.*;

import com.oracle.graal.jtt.*;

/*
 */
public class Loop03 extends JTTTest {

    public static int test(int count) {
        int i1 = 1;
        int i2 = 2;
        int i4 = 4;

        for (int i = 0; i < count; i++) {
            i1 = i2;
            i2 = 7;
            i4 = i1;
        }
        return i1 + i2 * 10 + i4 * 1000;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 10);
    }

}
