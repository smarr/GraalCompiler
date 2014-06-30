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

package com.oracle.graal.compiler.hsail.test;

import org.junit.*;

/**
 * A version which is likely to get a GC while running the never_rans, and so is good for oops_do
 * testing.
 */
public class BoundsCatchMost20000StressGCTest extends BoundsCatchManyBase {

    @Override
    int getGlobalSize() {
        return 20000;
    }

    boolean isMyDeoptGid(int gid) {
        return (gid > 100 && gid % 100 != 1);
    }

    int[] dummyArray;

    // copied run routine here because otherwise polymorphic calls to isDeoptGid
    @Override
    public void run(int gid) {
        int outval = getOutval(gid);
        try {
            int index = (isMyDeoptGid(gid) ? num + 1 : gid);
            outArray[index] = outval;
        } catch (ArrayIndexOutOfBoundsException e) {
            // set up so we can detect if we go thru here twice
            outArray[gid] += outval;
            // note: cannot record the exceptiongid here for many deopts in parallel

            // allocate something so GCs happen more often
            dummyArray = new int[1000];
        }
    }

    @Override
    public void runTest() {
        setupArrays();

        for (int i = 0; i < 10; i++) {
            // we should not get an exception escaping from the kernel
            dispatchMethodKernel(num);
        }
    }

    @Test
    public void test() {
        testGeneratedHsail();
    }
}
