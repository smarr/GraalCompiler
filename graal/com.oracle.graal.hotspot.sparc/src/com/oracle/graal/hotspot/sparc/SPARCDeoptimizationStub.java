/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.sparc;

import static jdk.internal.jvmci.sparc.SPARC.*;
import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.hotspot.sparc.*;

import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.stubs.*;

final class SPARCDeoptimizationStub extends DeoptimizationStub {

    private RegisterConfig registerConfig;

    public SPARCDeoptimizationStub(HotSpotProviders providers, TargetDescription target, HotSpotForeignCallLinkage linkage) {
        super(providers, target, linkage);
        // This is basically the maximum we can spare. All other G and O register are used.
        Register[] allocatable = new Register[]{g1, g3, g4, g5, o0, o1, o2, o3, o4};
        registerConfig = new SPARCHotSpotRegisterConfig(target, allocatable);
    }

    @Override
    public RegisterConfig getRegisterConfig() {
        return registerConfig;
    }

}
