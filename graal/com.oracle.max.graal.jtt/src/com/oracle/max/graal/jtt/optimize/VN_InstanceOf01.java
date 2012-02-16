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
package com.oracle.max.graal.jtt.optimize;

import org.junit.*;

/*
 * Tests value numbering of instanceof operations.
 */
public class VN_InstanceOf01 {

    static final Object object = new VN_InstanceOf01();

    public static boolean test(int arg) {
        if (arg == 0) {
            return foo1();
        }
        if (arg == 1) {
            return foo2();
        }
        if (arg == 2) {
            return foo3();
        }
        // do nothing
        return false;
    }

    private static boolean foo1() {
        boolean a = object instanceof VN_InstanceOf01;
        boolean b = object instanceof VN_InstanceOf01;
        return a | b;
    }

    private static boolean foo2() {
        Object obj = new VN_InstanceOf01();
        boolean a = obj instanceof VN_InstanceOf01;
        boolean b = obj instanceof VN_InstanceOf01;
        return a | b;
    }

    private static boolean foo3() {
        boolean a = null instanceof VN_InstanceOf01;
        boolean b = null instanceof VN_InstanceOf01;
        return a | b;
    }

    @Test
    public void run0() throws Throwable {
        Assert.assertEquals(true, test(0));
    }

    @Test
    public void run1() throws Throwable {
        Assert.assertEquals(true, test(1));
    }

    @Test
    public void run2() throws Throwable {
        Assert.assertEquals(false, test(2));
    }

}
