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
package com.oracle.graal.nodes.extended;

import com.oracle.graal.cri.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.max.cri.ci.*;

/**
 * Load of a value from a location specified as an offset relative to an object.
 */
public class UnsafeLoadNode extends FixedWithNextNode implements Lowerable {

    @Input private ValueNode object;
    @Input private ValueNode offset;
    private final int displacement;
    private final RiKind loadKind;

    public ValueNode object() {
        return object;
    }

    public int displacement() {
        return displacement;
    }

    public ValueNode offset() {
        return offset;
    }

    public UnsafeLoadNode(ValueNode object, int displacement, ValueNode offset, boolean nonNull) {
        super(nonNull ? StampFactory.objectNonNull() : StampFactory.object());
        this.object = object;
        this.displacement = displacement;
        this.offset = offset;
        this.loadKind = RiKind.Object;
    }

    public UnsafeLoadNode(ValueNode object, int displacement, ValueNode offset, RiKind kind) {
        super(StampFactory.forKind(kind.stackKind()));
        this.object = object;
        this.displacement = displacement;
        this.offset = offset;
        this.loadKind = kind;
    }

    public RiKind loadKind() {
        return loadKind;
    }

    @Override
    public void lower(CiLoweringTool tool) {
        tool.getRuntime().lower(this, tool);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static <T> T load(Object object, @ConstantNodeParameter int displacement, long offset, @ConstantNodeParameter RiKind kind) {
        throw new UnsupportedOperationException("This method may only be compiled with the Graal compiler");
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static Object loadObject(Object object, @ConstantNodeParameter int displacement, long offset, @ConstantNodeParameter boolean nonNull) {
        throw new UnsupportedOperationException("This method may only be compiled with the Graal compiler");
    }
}
