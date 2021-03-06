/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.calc;

import jdk.internal.jvmci.common.JVMCIError;
import jdk.internal.jvmci.meta.ConstantReflectionProvider;
import jdk.internal.jvmci.meta.TriState;

import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.compiler.common.type.FloatStamp;
import com.oracle.graal.compiler.common.type.IntegerStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.LogicConstantNode;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.util.GraphUtil;

@NodeInfo(shortName = "<")
public final class FloatLessThanNode extends CompareNode {
    public static final NodeClass<FloatLessThanNode> TYPE = NodeClass.create(FloatLessThanNode.class);

    public FloatLessThanNode(ValueNode x, ValueNode y, boolean unorderedIsTrue) {
        super(TYPE, Condition.LT, unorderedIsTrue, x, y);
        assert x.stamp() instanceof FloatStamp && y.stamp() instanceof FloatStamp;
        assert x.stamp().isCompatible(y.stamp());
    }

    public static LogicNode create(ValueNode x, ValueNode y, boolean unorderedIsTrue, ConstantReflectionProvider constantReflection) {
        LogicNode result = CompareNode.tryConstantFold(Condition.LT, x, y, constantReflection, unorderedIsTrue);
        if (result != null) {
            return result;
        } else {
            return new FloatLessThanNode(x, y, unorderedIsTrue);
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode result = super.canonical(tool, forX, forY);
        if (result != this) {
            return result;
        }
        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY) && !unorderedIsTrue()) {
            return LogicConstantNode.contradiction();
        }
        return this;
    }

    @Override
    protected CompareNode duplicateModified(ValueNode newX, ValueNode newY) {
        if (newX.stamp() instanceof FloatStamp && newY.stamp() instanceof FloatStamp) {
            return new FloatLessThanNode(newX, newY, unorderedIsTrue);
        } else if (newX.stamp() instanceof IntegerStamp && newY.stamp() instanceof IntegerStamp) {
            return new IntegerLessThanNode(newX, newY);
        }
        throw JVMCIError.shouldNotReachHere();
    }

    @Override
    public Stamp getSucceedingStampForX(boolean negated) {
        return null;
    }

    @Override
    public Stamp getSucceedingStampForY(boolean negated) {
        return null;
    }

    @Override
    public TriState tryFold(Stamp xStampGeneric, Stamp yStampGeneric) {
        return TriState.UNKNOWN;
    }
}
