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
package com.oracle.graal.asm.ptx;

import com.oracle.graal.api.code.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.lir.ptx.*;

/**
 * The platform-dependent base class for the PTX assembler.
 */
public abstract class AbstractPTXAssembler extends AbstractAssembler {

    public AbstractPTXAssembler(TargetDescription target) {
        super(target);
    }

    @Override
    public final void bind(Label l) {
        super.bind(l);
        emitString0("L"+l.toString() + ":\n");
    }

    @Override
    public void align(int modulus) {
        // TODO Auto-generated method stub
    }

    @Override
    public void jmp(Label l) {
        // TODO Auto-generated method stub
    }

    @Override
    protected void patchJumpTarget(int branch, int jumpTarget) {
        final int spaces = PTXControlFlow.BranchOp.UNBOUND_TARGET.length();
        String target = String.format("L%-" + spaces + "s", jumpTarget+";");
        int offset = "\tbra ".length();  // XXX we need a better way to figure this out
        codeBuffer.emitString(target, branch + offset);
    }

    @Override
    public void bangStack(int disp) {
        // TODO Auto-generated method stub
    }

}
