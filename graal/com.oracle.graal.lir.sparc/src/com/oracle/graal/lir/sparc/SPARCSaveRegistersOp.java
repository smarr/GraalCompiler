/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.STACK;
import static com.oracle.graal.lir.sparc.SPARCDelayedControlTransfer.DUMMY;
import static jdk.internal.jvmci.code.ValueUtil.asStackSlot;
import static jdk.internal.jvmci.code.ValueUtil.isStackSlot;

import java.util.Arrays;
import java.util.Set;

import jdk.internal.jvmci.code.Register;
import jdk.internal.jvmci.code.RegisterSaveLayout;
import jdk.internal.jvmci.code.RegisterValue;
import jdk.internal.jvmci.code.StackSlot;
import jdk.internal.jvmci.code.StackSlotValue;
import jdk.internal.jvmci.code.ValueUtil;
import jdk.internal.jvmci.sparc.SPARC;

import com.oracle.graal.asm.sparc.SPARCAddress;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.StandardOp.SaveRegistersOp;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
import com.oracle.graal.lir.framemap.FrameMap;

/**
 * Saves registers to stack slots.
 */
@Opcode("SAVE_REGISTER")
public class SPARCSaveRegistersOp extends SPARCLIRInstruction implements SaveRegistersOp {
    public static final LIRInstructionClass<SPARCSaveRegistersOp> TYPE = LIRInstructionClass.create(SPARCSaveRegistersOp.class);
    public static final Register RETURN_REGISTER_STORAGE = SPARC.d62;
    public static final SizeEstimate SIZE = SizeEstimate.create(32);
    /**
     * The registers (potentially) saved by this operation.
     */
    protected final Register[] savedRegisters;

    /**
     * The slots to which the registers are saved.
     */
    @Def(STACK) protected final StackSlotValue[] slots;

    /**
     * Specifies if {@link #remove(Set)} should have an effect.
     */
    protected final boolean supportsRemove;

    /**
     *
     * @param savedRegisters the registers saved by this operation which may be subject to
     *            {@linkplain #remove(Set) pruning}
     * @param savedRegisterLocations the slots to which the registers are saved
     * @param supportsRemove determines if registers can be {@linkplain #remove(Set) pruned}
     */
    public SPARCSaveRegistersOp(Register[] savedRegisters, StackSlotValue[] savedRegisterLocations, boolean supportsRemove) {
        super(TYPE, SIZE);
        assert Arrays.asList(savedRegisterLocations).stream().allMatch(ValueUtil::isVirtualStackSlot);
        this.savedRegisters = savedRegisters;
        this.slots = savedRegisterLocations;
        this.supportsRemove = supportsRemove;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        // Can be used with VIS3
        // new Movxtod(SPARC.i0, RETURN_REGISTER_STORAGE).emit(masm);
        // We abuse the first stackslot for transferring i0 to return_register_storage
        // assert slots.length >= 1;
        SPARCAddress slot0Address = (SPARCAddress) crb.asAddress(slots[0]);
        masm.stx(SPARC.i0, slot0Address);
        masm.lddf(slot0Address, RETURN_REGISTER_STORAGE);

        // Now save the registers
        for (int i = 0; i < savedRegisters.length; i++) {
            if (savedRegisters[i] != null) {
                assert isStackSlot(slots[i]) : "not a StackSlot: " + slots[i];
                Register savedRegister = savedRegisters[i];
                StackSlot slot = asStackSlot(slots[i]);
                SPARCAddress slotAddress = (SPARCAddress) crb.asAddress(slot);
                RegisterValue input = savedRegister.asValue(slot.getLIRKind());
                SPARCMove.emitStore(input, slotAddress, slot.getPlatformKind(), DUMMY, null, crb, masm);
            }
        }
    }

    public StackSlotValue[] getSlots() {
        return slots;
    }

    public boolean supportsRemove() {
        return supportsRemove;
    }

    public int remove(Set<Register> doNotSave) {
        if (!supportsRemove) {
            throw new UnsupportedOperationException();
        }
        return prune(doNotSave, savedRegisters);
    }

    static int prune(Set<Register> toRemove, Register[] registers) {
        int pruned = 0;
        for (int i = 0; i < registers.length; i++) {
            if (registers[i] != null) {
                if (toRemove.contains(registers[i])) {
                    registers[i] = null;
                    pruned++;
                }
            }
        }
        return pruned;
    }

    public RegisterSaveLayout getMap(FrameMap frameMap) {
        int total = 0;
        for (int i = 0; i < savedRegisters.length; i++) {
            if (savedRegisters[i] != null) {
                total++;
            }
        }
        Register[] keys = new Register[total];
        int[] values = new int[total];
        if (total != 0) {
            int mapIndex = 0;
            for (int i = 0; i < savedRegisters.length; i++) {
                if (savedRegisters[i] != null) {
                    keys[mapIndex] = savedRegisters[i];
                    assert isStackSlot(slots[i]) : "not a StackSlot: " + slots[i];
                    StackSlot slot = asStackSlot(slots[i]);
                    values[mapIndex] = indexForStackSlot(frameMap, slot);
                    mapIndex++;
                }
            }
            assert mapIndex == total;
        }
        return new RegisterSaveLayout(keys, values);
    }

    /**
     * Computes the index of a stack slot relative to slot 0. This is also the bit index of stack
     * slots in the reference map.
     *
     * @param slot a stack slot
     * @return the index of the stack slot
     */
    private static int indexForStackSlot(FrameMap frameMap, StackSlot slot) {
        assert frameMap.offsetForStackSlot(slot) % frameMap.getTarget().wordSize == 0;
        int value = frameMap.offsetForStackSlot(slot) / frameMap.getTarget().wordSize;
        return value;
    }
}
