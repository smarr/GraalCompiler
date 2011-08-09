/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.runtime.nodes;

import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.nodes.base.*;
import com.oracle.max.graal.compiler.nodes.spi.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;


public final class ArrayWriteBarrier extends WriteBarrier {

    @Input private ValueNode object;
    @Input private LocationNode location;

    public ValueNode object() {
        return object;
    }

    public void setObject(ValueNode x) {
        updateUsages(this.object, x);
        this.object = x;
    }

    public LocationNode location() {
        return location;
    }

    public void setLocation(LocationNode x) {
        updateUsages(location, x);
        location = x;
    }

    public ArrayWriteBarrier(ValueNode object, LocationNode index, Graph graph) {
        super(graph);
        this.setObject(object);
        this.setLocation(index);
    }


    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == LIRGeneratorOp.class) {
            return (T) new LIRGeneratorOp() {
                @Override
                public void generate(Node n, LIRGeneratorTool generator) {
                    assert n == ArrayWriteBarrier.this;
                    CiVariable temp = generator.newVariable(CiKind.Word);
                    generator.emitLea(location().createAddress(generator, object()), temp);
                    ArrayWriteBarrier.this.generateBarrier(temp, generator);
                }
            };
        }
        return super.lookup(clazz);
    }
}
