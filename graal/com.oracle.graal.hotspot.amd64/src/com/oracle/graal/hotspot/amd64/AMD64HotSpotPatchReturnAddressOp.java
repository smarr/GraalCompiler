/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import static jdk.internal.jvmci.amd64.AMD64.rsp;
import static jdk.internal.jvmci.code.ValueUtil.asRegister;
import jdk.internal.jvmci.meta.AllocatableValue;

import com.oracle.graal.asm.amd64.AMD64Address;
import com.oracle.graal.asm.amd64.AMD64MacroAssembler;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.amd64.AMD64LIRInstruction;
import com.oracle.graal.lir.asm.CompilationResultBuilder;

/**
 * Patch the return address of the current frame.
 */
@Opcode("PATCH_RETURN")
final class AMD64HotSpotPatchReturnAddressOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64HotSpotPatchReturnAddressOp> TYPE = LIRInstructionClass.create(AMD64HotSpotPatchReturnAddressOp.class);

    @Use(REG) AllocatableValue address;

    AMD64HotSpotPatchReturnAddressOp(AllocatableValue address) {
        super(TYPE);
        this.address = address;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        int frameSize = crb.frameMap.frameSize();
        masm.movq(new AMD64Address(rsp, frameSize), asRegister(address));
    }
}
