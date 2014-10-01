/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.java;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * The {@code StoreFieldNode} represents a write to a static or instance field.
 */
@NodeInfo(nameTemplate = "StoreField#{p#field/s}")
public class StoreFieldNode extends AccessFieldNode implements StateSplit, VirtualizableRoot {

    @Input ValueNode value;
    @OptionalInput(InputType.State) FrameState stateAfter;

    public FrameState stateAfter() {
        return stateAfter;
    }

    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    public boolean hasSideEffect() {
        return true;
    }

    public ValueNode value() {
        return value;
    }

    /**
     * Creates a new StoreFieldNode.
     *
     * @param object the receiver object
     * @param field the compiler interface field
     * @param value the node representing the value to store to the field
     */
    public static StoreFieldNode create(ValueNode object, ResolvedJavaField field, ValueNode value) {
        return USE_GENERATED_NODES ? new StoreFieldNodeGen(object, field, value) : new StoreFieldNode(object, field, value);
    }

    protected StoreFieldNode(ValueNode object, ResolvedJavaField field, ValueNode value) {
        super(StampFactory.forVoid(), object, field);
        this.value = value;
    }

    public static StoreFieldNode create(ValueNode object, ResolvedJavaField field, ValueNode value, FrameState stateAfter) {
        return USE_GENERATED_NODES ? new StoreFieldNodeGen(object, field, value, stateAfter) : new StoreFieldNode(object, field, value, stateAfter);
    }

    protected StoreFieldNode(ValueNode object, ResolvedJavaField field, ValueNode value, FrameState stateAfter) {
        super(StampFactory.forVoid(), object, field);
        this.value = value;
        this.stateAfter = stateAfter;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object());
        if (state != null && state.getState() == EscapeState.Virtual) {
            int fieldIndex = ((VirtualInstanceNode) state.getVirtualObject()).fieldIndex(field());
            if (fieldIndex != -1) {
                tool.setVirtualEntry(state, fieldIndex, value(), false);
                tool.delete();
            }
        }
    }

    public FrameState getState() {
        return stateAfter;
    }
}
