/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.jvmci.meta.ConstantReflectionProvider;
import jdk.internal.jvmci.meta.JavaConstant;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.ValueProxyNode;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.nodes.spi.ValueProxy;
import com.oracle.graal.nodes.spi.Virtualizable;
import com.oracle.graal.nodes.spi.VirtualizerTool;
import com.oracle.graal.nodes.util.GraphUtil;

/**
 * The {@code ArrayLength} instruction gets the length of an array.
 */
@NodeInfo
public final class ArrayLengthNode extends FixedWithNextNode implements Canonicalizable.Unary<ValueNode>, Lowerable, Virtualizable {

    public static final NodeClass<ArrayLengthNode> TYPE = NodeClass.create(ArrayLengthNode.class);
    @Input ValueNode array;

    public ValueNode array() {
        return array;
    }

    public ValueNode getValue() {
        return array;
    }

    public ArrayLengthNode(ValueNode array) {
        super(TYPE, StampFactory.positiveInt());
        this.array = array;
    }

    public static ValueNode create(ValueNode forValue, ConstantReflectionProvider constantReflection) {
        if (forValue instanceof NewArrayNode) {
            NewArrayNode newArray = (NewArrayNode) forValue;
            return newArray.length();
        }

        ValueNode length = readArrayLengthConstant(forValue, constantReflection);
        if (length != null) {
            return length;
        }
        return new ArrayLengthNode(forValue);
    }

    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode length = readArrayLength(forValue, tool.getConstantReflection());
        if (length != null) {
            return length;
        }
        return this;
    }

    /**
     * Replicate the {@link ValueProxyNode}s from {@code originalValue} onto {@code value}.
     *
     * @param originalValue a possibly proxied value
     * @param value a value needing proxies
     * @return proxies wrapping {@code value}
     */
    private static ValueNode reproxyValue(ValueNode originalValue, ValueNode value) {
        if (value.isConstant()) {
            // No proxy needed
            return value;
        }
        if (originalValue instanceof ValueProxyNode) {
            ValueProxyNode proxy = (ValueProxyNode) originalValue;
            return new ValueProxyNode(reproxyValue(proxy.getOriginalNode(), value), proxy.proxyPoint());
        } else if (originalValue instanceof ValueProxy) {
            ValueProxy proxy = (ValueProxy) originalValue;
            return reproxyValue(proxy.getOriginalNode(), value);
        } else {
            return value;
        }
    }

    /**
     * Gets the length of an array if possible.
     *
     * @return a node representing the length of {@code array} or null if it is not available
     */
    public static ValueNode readArrayLength(ValueNode originalArray, ConstantReflectionProvider constantReflection) {
        ValueNode length = GraphUtil.arrayLength(originalArray);
        if (length != null) {
            // Ensure that any proxies on the original value end up on the length value
            return reproxyValue(originalArray, length);
        }
        return readArrayLengthConstant(originalArray, constantReflection);
    }

    private static ValueNode readArrayLengthConstant(ValueNode originalArray, ConstantReflectionProvider constantReflection) {
        ValueNode array = GraphUtil.unproxify(originalArray);
        if (constantReflection != null && array.isConstant() && !array.isNullConstant()) {
            JavaConstant constantValue = array.asJavaConstant();
            if (constantValue != null && constantValue.isNonNull()) {
                Integer constantLength = constantReflection.readArrayLength(constantValue);
                if (constantLength != null) {
                    return ConstantNode.forInt(constantLength);
                }
            }
        }
        return null;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @NodeIntrinsic
    public static native int arrayLength(Object array);

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(array());
        ValueNode length = GraphUtil.arrayLength(alias);
        if (length != null) {
            ValueNode lengthAlias = tool.getAlias(length);
            if (!lengthAlias.isAlive()) {
                lengthAlias = graph().addOrUnique(lengthAlias);
            }
            tool.replaceWithValue(lengthAlias);
        }
    }
}
