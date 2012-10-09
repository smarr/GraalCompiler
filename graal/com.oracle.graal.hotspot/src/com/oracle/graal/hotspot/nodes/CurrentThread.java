/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

public final class CurrentThread extends FloatingNode implements LIRLowerable {

    private int threadObjectOffset;

    public CurrentThread(int threadObjectOffset, CodeCacheProvider runtime) {
        super(StampFactory.declaredNonNull(runtime.lookupJavaType(Thread.class)));
        this.threadObjectOffset = threadObjectOffset;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        Register thread = HotSpotGraalRuntime.getInstance().getRuntime().threadRegister();
        gen.setResult(this, gen.emitLoad(new Address(Kind.Object, thread.asValue(gen.target().wordKind), threadObjectOffset), false));
    }

    @NodeIntrinsic
    public static native Object get(@ConstantNodeParameter int threadObjectOffset);
}
