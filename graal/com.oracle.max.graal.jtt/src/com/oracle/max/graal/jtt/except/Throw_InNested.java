/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
 */
package com.oracle.max.graal.jtt.except;

import org.junit.*;

public class Throw_InNested {

    public static int test(int i) throws Exception {
        return 42 + test2(i);
    }

    public static int test2(int i) throws Exception {
        try {
            return test3(i);
        } catch (Exception e) {
            return 5;
        }
    }

    private static int test3(int i) {
        if (i == 0) {
            throw new RuntimeException();
        }
        return i;
    }

    @Test
    public void run0() throws Throwable {
        Assert.assertEquals(47, test(0));
    }

    @Test
    public void run1() throws Throwable {
        Assert.assertEquals(43, test(1));
    }

}
