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
package com.oracle.graal.lir.sparc;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import static jdk.internal.jvmci.code.ValueUtil.asRegister;
import jdk.internal.jvmci.meta.Value;

import com.oracle.graal.asm.sparc.SPARCAssembler;
import com.oracle.graal.asm.sparc.SPARCAssembler.Opfs;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.asm.CompilationResultBuilder;

public class SPARCOPFOp extends SPARCLIRInstruction {
    public static final LIRInstructionClass<SPARCOPFOp> TYPE = LIRInstructionClass.create(SPARCOPFOp.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(1);

    @Opcode protected final Opfs opf;
    @Use({REG}) protected Value a;
    @Use({REG}) protected Value b;
    @Use({REG}) protected Value result;

    public SPARCOPFOp(Opfs opf, Value a, Value b, Value result) {
        super(TYPE, SIZE);
        this.opf = opf;
        this.a = a;
        this.b = b;
        this.result = result;
    }

    @Override
    protected void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        SPARCAssembler.OPF.emit(masm, opf, asRegister(a), asRegister(b), asRegister(result));
    }
}
