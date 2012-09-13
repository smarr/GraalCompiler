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

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * The {@code NewArrayNode} class is the base of all instructions that allocate arrays.
 */
public abstract class NewArrayNode extends FixedWithNextNode implements Lowerable, EscapeAnalyzable, ArrayLengthProvider {

    @Input private ValueNode length;
    private final ResolvedJavaType elementType;
    private final boolean fillContents;

    public static final int MaximumEscapeAnalysisArrayLength = 32;

    @Override
    public ValueNode length() {
        return length;
    }

    /**
     * Constructs a new NewArrayNode.
     * @param length the node that produces the length for this allocation
     */
    protected NewArrayNode(ResolvedJavaType elementType, ValueNode length, boolean fillContents) {
        super(StampFactory.exactNonNull(elementType.arrayOf()));
        this.length = length;
        this.elementType = elementType;
        this.fillContents = fillContents;
    }

    public boolean fillContents() {
        return fillContents;
    }

    /**
     * The list of node which produce input for this instruction.
     */
    public ValueNode dimension(int index) {
        assert index == 0;
        return length();
    }

    /**
     * Gets the element type of the array.
     * @return the element type of the array
     */
    public ResolvedJavaType elementType() {
        return elementType;
    }

    /**
     * The rank of the array allocated by this node, i.e. how many array dimensions.
     */
    public int dimensionCount() {
        return 1;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getRuntime().lower(this, tool);
    }

    @Override
    public EscapeOp getEscapeOp() {
        Constant constantLength = length().asConstant();
        if (constantLength != null && constantLength.asInt() >= 0 && constantLength.asInt() < MaximumEscapeAnalysisArrayLength) {
            return new EscapeOpImpl();
        } else {
            return null;
        }
    }

    private final class EscapeOpImpl extends EscapeOp {

        @Override
        public ResolvedJavaType type() {
            return elementType.arrayOf();
        }

        @Override
        public EscapeField[] fields() {
            int constantLength = dimension(0).asConstant().asInt();
            EscapeField[] fields = new EscapeField[constantLength];
            for (int i = 0; i < constantLength; i++) {
                Integer representation = i;
                fields[i] = new EscapeField(Integer.toString(i), representation, elementType());
            }
            return fields;
        }

        @Override
        public ValueNode[] fieldState() {
            ValueNode[] state = new ValueNode[dimension(0).asConstant().asInt()];
            for (int i = 0; i < state.length; i++) {
                state[i] = ConstantNode.defaultForKind(elementType().kind(), graph());
            }
            return state;
        }

        @Override
        public void beforeUpdate(Node usage) {
            if (usage instanceof ArrayLengthNode) {
                ArrayLengthNode x = (ArrayLengthNode) usage;
                StructuredGraph graph = (StructuredGraph) graph();
                x.replaceAtUsages(dimension(0));
                graph.removeFixed(x);
            } else {
                beforeUpdate(NewArrayNode.this, usage);
            }
        }

        @Override
        public int updateState(VirtualObjectNode virtualObject, Node current, Map<Object, Integer> fieldIndex, ValueNode[] fieldState) {
            if (current instanceof AccessIndexedNode) {
                AccessIndexedNode x = (AccessIndexedNode) current;
                if (GraphUtil.unProxify(x.array()) == virtualObject) {
                    int index = ((AccessIndexedNode) current).index().asConstant().asInt();
                    StructuredGraph graph = (StructuredGraph) x.graph();
                    if (current instanceof LoadIndexedNode) {
                        x.replaceAtUsages(fieldState[index]);
                        graph.removeFixed(x);
                    } else if (current instanceof StoreIndexedNode) {
                        fieldState[index] = ((StoreIndexedNode) x).value();
                        graph.removeFixed(x);
                        return index;
                    }
                }
            }
            return -1;
        }

    }
}
