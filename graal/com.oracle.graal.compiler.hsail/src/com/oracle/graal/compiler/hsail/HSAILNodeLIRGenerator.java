/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.hsail;

import static com.oracle.graal.lir.hsail.HSAILBitManipulationOp.IntrinsicOpcode.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.hsail.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;

/**
 * This class implements the HSAIL specific portion of the LIR generator.
 */
public abstract class HSAILNodeLIRGenerator extends NodeLIRGenerator {

    public HSAILNodeLIRGenerator(StructuredGraph graph, LIRGenerationResult lirGenRes, LIRGenerator lirGen) {
        super(graph, lirGenRes, lirGen);
    }

    @Override
    protected boolean peephole(ValueNode valueNode) {
        // No peephole optimizations for now.
        return false;
    }

    @Override
    protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    public void emitBitCount(Variable result, Value value) {
        if (value.getKind().getStackKind() == Kind.Int) {
            append(new HSAILBitManipulationOp(IPOPCNT, result, value));
        } else {
            append(new HSAILBitManipulationOp(LPOPCNT, result, value));
        }
    }

    @Override
    public void emitBitScanForward(Variable result, Value value) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    public void emitBitScanReverse(Variable result, Value value) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    public void emitArrayEquals(Kind kind, Variable result, Value array1, Value array2, Value length) {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented();
    }

    @Override
    public void visitBreakpointNode(BreakpointNode node) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    public void visitSafepointNode(SafepointNode i) {
        Debug.log("visitSafePointNode unimplemented");
    }

    @Override
    public void emitNullCheck(ValueNode v, DeoptimizingNode deopting) {
        assert v.stamp() instanceof ObjectStamp;
        Variable obj = newVariable(Kind.Object);
        gen.emitMove(obj, operand(v));
        append(new HSAILMove.NullCheckOp(obj, state(deopting)));
    }

    @Override
    public void visitInfopointNode(InfopointNode i) {
        throw GraalInternalError.unimplemented();
    }
}
