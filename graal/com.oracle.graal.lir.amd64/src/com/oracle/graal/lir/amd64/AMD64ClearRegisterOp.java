/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.amd64;

import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.XOR;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import static jdk.internal.jvmci.code.ValueUtil.asRegister;
import jdk.internal.jvmci.meta.AllocatableValue;

import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp;
import com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize;
import com.oracle.graal.asm.amd64.AMD64MacroAssembler;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.asm.CompilationResultBuilder;

public class AMD64ClearRegisterOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64ClearRegisterOp> TYPE = LIRInstructionClass.create(AMD64ClearRegisterOp.class);

    @Opcode private final AMD64RMOp op;
    private final OperandSize size;

    @Def({REG}) protected AllocatableValue result;

    public AMD64ClearRegisterOp(OperandSize size, AllocatableValue result) {
        super(TYPE);
        this.op = XOR.getRMOpcode(size);
        this.size = size;
        this.result = result;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        op.emit(masm, size, asRegister(result), asRegister(result));
    }
}
