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
package com.oracle.max.graal.jtt.bytecode;

import org.junit.*;

/*
 */
public class BC_ladd2 {

    public static long test(int a, int b) {
        return a + (long) b;
    }

    @Test
    public void run0() throws Throwable {
        Assert.assertEquals(3L, test(1, 2));
    }

    @Test
    public void run1() throws Throwable {
        Assert.assertEquals(-1L, test(0, -1));
    }

    @Test
    public void run2() throws Throwable {
        Assert.assertEquals(100L, test(33, 67));
    }

    @Test
    public void run3() throws Throwable {
        Assert.assertEquals(0L, test(1, -1));
    }

    @Test
    public void run4() throws Throwable {
        Assert.assertEquals(-2147483647L, test(-2147483648, 1));
    }

    @Test
    public void run5() throws Throwable {
        Assert.assertEquals(2147483648L, test(2147483647, 1));
    }

    @Test
    public void run6() throws Throwable {
        Assert.assertEquals(-2147483649L, test(-2147483647, -2));
    }

}
