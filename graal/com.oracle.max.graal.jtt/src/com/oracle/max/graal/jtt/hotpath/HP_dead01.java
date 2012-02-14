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
// Checkstyle: stop
package com.oracle.max.graal.jtt.hotpath;

import org.junit.*;

/*
 */
public class HP_dead01 {

    public static int test(int count) {
        int sum = 0;
        for (int i = 0; i <= count; i++) {
            int a = i + i;
            int b = i / 2 * i - 10;
            @SuppressWarnings("unused")
            int c = a + b;
            int d = a;
            sum += d;
        }
        return sum;
    }

    @Test
    public void run0() throws Throwable {
        Assert.assertEquals(110, test(10));
    }

    @Test
    public void run1() throws Throwable {
        Assert.assertEquals(420, test(20));
    }

    @Test
    public void run2() throws Throwable {
        Assert.assertEquals(930, test(30));
    }

    @Test
    public void run3() throws Throwable {
        Assert.assertEquals(1640, test(40));
    }

}
