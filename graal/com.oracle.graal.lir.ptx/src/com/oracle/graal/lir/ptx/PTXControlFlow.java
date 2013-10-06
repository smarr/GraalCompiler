/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.ptx;

import static com.oracle.graal.asm.ptx.PTXAssembler.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static com.oracle.graal.nodes.calc.Condition.*;

import com.oracle.graal.api.code.CompilationResult.JumpTable;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.ptx.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.FallThroughOp;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.calc.*;

public class PTXControlFlow {

    public static class ReturnOp extends PTXLIRInstruction {

        @Use({REG, ILLEGAL}) protected Value x;

        public ReturnOp(Value x) {
            this.x = x;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            if (tasm.frameContext != null) {
                tasm.frameContext.leave(tasm);
            }
            masm.exit();
        }
    }

    public static class ReturnNoValOp extends PTXLIRInstruction {

        public ReturnNoValOp() { }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            if (tasm.frameContext != null) {
                tasm.frameContext.leave(tasm);
            }
            masm.ret();
        }
    }

    public static class BranchOp extends PTXLIRInstruction implements StandardOp.BranchOp {

        protected Condition condition;
        protected LabelRef destination;
        protected int predRegNum;

        public BranchOp(Condition condition, LabelRef destination, int predReg) {
            this.condition = condition;
            this.destination = destination;
            this.predRegNum = predReg;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            masm.bra(masm.nameOf(destination.label()), predRegNum);
        }

        @Override
        public LabelRef destination() {
            return destination;
        }

        @Override
        public void negate(LabelRef newDestination) {
            destination = newDestination;
            condition = condition.negate();
        }
    }

    @SuppressWarnings("unused")
    public static class CondMoveOp extends PTXLIRInstruction {

        @Def({REG, HINT}) protected Value result;
        @Alive({REG}) protected Value trueValue;
        @Use({REG, STACK, CONST}) protected Value falseValue;
        private final Condition condition;

        public CondMoveOp(Variable result, Condition condition, Variable trueValue, Value falseValue) {
            this.result = result;
            this.condition = condition;
            this.trueValue = trueValue;
            this.falseValue = falseValue;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            // cmove(tasm, masm, result, false, condition, false, trueValue, falseValue);
            // see 8.3 Predicated Execution p. 61 of PTX ISA 3.1
            throw new InternalError("NYI");
        }
    }

    @SuppressWarnings("unused")
    public static class FloatCondMoveOp extends PTXLIRInstruction {

        @Def({REG}) protected Value result;
        @Alive({REG}) protected Value trueValue;
        @Alive({REG}) protected Value falseValue;
        private final Condition condition;
        private final boolean unorderedIsTrue;

        public FloatCondMoveOp(Variable result, Condition condition, boolean unorderedIsTrue, Variable trueValue, Variable falseValue) {
            this.result = result;
            this.condition = condition;
            this.unorderedIsTrue = unorderedIsTrue;
            this.trueValue = trueValue;
            this.falseValue = falseValue;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            // cmove(tasm, masm, result, true, condition, unorderedIsTrue, trueValue, falseValue);
            // see 8.3 Predicated Execution p. 61 of PTX ISA 3.1
            throw new InternalError("NYI");
        }
    }

    public static class SequentialSwitchOp extends PTXLIRInstruction implements FallThroughOp {

        @Use({CONST}) protected Constant[] keyConstants;
        private final LabelRef[] keyTargets;
        private LabelRef defaultTarget;
        @Alive({REG}) protected Value key;
        @Temp({REG, ILLEGAL}) protected Value scratch;
        // Number of predicate register that would be set by this instruction.
        protected int predRegNum;

        public SequentialSwitchOp(Constant[] keyConstants, LabelRef[] keyTargets, LabelRef defaultTarget, Value key, Value scratch, int predReg) {
            assert keyConstants.length == keyTargets.length;
            this.keyConstants = keyConstants;
            this.keyTargets = keyTargets;
            this.defaultTarget = defaultTarget;
            this.key = key;
            this.scratch = scratch;
            predRegNum = predReg;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            Kind keyKind = key.getKind();

            if (keyKind == Kind.Int || keyKind == Kind.Long) {
                for (int i = 0; i < keyConstants.length; i++) {
                    if (tasm.runtime.needsDataPatch(keyConstants[i])) {
                        tasm.recordDataReferenceInCode(keyConstants[i], 0, true);
                    }
                    new Setp(EQ, keyConstants[i], key, predRegNum).emit(masm);
                    masm.bra(masm.nameOf(keyTargets[i].label()), predRegNum);
                }
            } else if (keyKind == Kind.Object) {
                for (int i = 0; i < keyConstants.length; i++) {
                    PTXMove.move(tasm, masm, scratch, keyConstants[i]);
                    new Setp(EQ, keyConstants[i], scratch, predRegNum).emit(masm);
                    masm.bra(keyTargets[i].label().toString(), predRegNum);
                }
            } else {
                throw new GraalInternalError("sequential switch only supported for int, long and object");
            }
            if (defaultTarget != null) {
                masm.jmp(defaultTarget.label());
            } else {
                // masm.hlt();
            }
        }

        @Override
        public LabelRef fallThroughTarget() {
            return defaultTarget;
        }

        @Override
        public void setFallThroughTarget(LabelRef target) {
            defaultTarget = target;
        }
    }

    public static class TableSwitchOp extends PTXLIRInstruction {

        private final int lowKey;
        private final LabelRef defaultTarget;
        private final LabelRef[] targets;
        @Alive protected Value index;
        @Temp protected Value scratch;
        // Number of predicate register that would be set by this instruction.
        protected int predRegNum;

        public TableSwitchOp(final int lowKey, final LabelRef defaultTarget, final LabelRef[] targets, Variable index, Variable scratch, int predReg) {
            this.lowKey = lowKey;
            this.defaultTarget = defaultTarget;
            this.targets = targets;
            this.index = index;
            this.scratch = scratch;
            predRegNum = predReg;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            tableswitch(tasm, masm, lowKey, defaultTarget, targets, index, scratch, predRegNum);
        }
    }

    @SuppressWarnings("unused")
    private static void tableswitch(TargetMethodAssembler tasm, PTXAssembler masm, int lowKey,
                                    LabelRef defaultTarget, LabelRef[] targets,
                                    Value value, Value scratch, int predNum) {
        Buffer buf = masm.codeBuffer;
        // Compare index against jump table bounds
        int highKey = lowKey + targets.length - 1;
        if (lowKey != 0) {
            // subtract the low value from the switch value
            // new Sub(value, value, lowKey).emit(masm);
            new Setp(GT, value, Constant.forInt(highKey - lowKey), predNum).emit(masm);
        } else {
            new Setp(GT, value, Constant.forInt(highKey), predNum).emit(masm);
        }

        // Jump to default target if index is not within the jump table
        if (defaultTarget != null) {
            masm.bra(defaultTarget.label().toString(), predNum);
        }

        // address of jump table
        int tablePos = buf.position();

        JumpTable jt = new JumpTable(tablePos, lowKey, highKey, 4);
        tasm.compilationResult.addAnnotation(jt);

        // PTX: unimp: tableswitch extract
    }
}
