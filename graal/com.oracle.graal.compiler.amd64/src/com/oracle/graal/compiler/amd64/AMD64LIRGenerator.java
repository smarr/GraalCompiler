/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.amd64;

import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.ADD;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.AND;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.CMP;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.OR;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.SUB;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.XOR;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64MOp.NEG;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64MOp.NOT;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.BSF;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.BSR;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.LZCNT;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.MOV;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.MOVSD;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.MOVSS;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.MOVSX;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.MOVSXB;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.MOVSXD;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.MOVZX;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.MOVZXB;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.POPCNT;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.TEST;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.TESTB;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.TZCNT;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64Shift.ROL;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64Shift.ROR;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64Shift.SAR;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64Shift.SHL;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64Shift.SHR;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.BYTE;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.DWORD;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.PD;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.PS;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.QWORD;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.SD;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.SS;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.WORD;
import static com.oracle.graal.lir.LIRValueUtil.asConstantValue;
import static com.oracle.graal.lir.LIRValueUtil.asJavaConstant;
import static com.oracle.graal.lir.LIRValueUtil.isJavaConstant;
import static com.oracle.graal.lir.amd64.AMD64Arithmetic.DREM;
import static com.oracle.graal.lir.amd64.AMD64Arithmetic.FREM;
import static com.oracle.graal.lir.amd64.AMD64MathIntrinsicOp.IntrinsicOpcode.COS;
import static com.oracle.graal.lir.amd64.AMD64MathIntrinsicOp.IntrinsicOpcode.LOG;
import static com.oracle.graal.lir.amd64.AMD64MathIntrinsicOp.IntrinsicOpcode.LOG10;
import static com.oracle.graal.lir.amd64.AMD64MathIntrinsicOp.IntrinsicOpcode.SIN;
import static com.oracle.graal.lir.amd64.AMD64MathIntrinsicOp.IntrinsicOpcode.TAN;
import static jdk.internal.jvmci.code.ValueUtil.isAllocatableValue;
import static jdk.internal.jvmci.code.ValueUtil.isRegister;
import static jdk.internal.jvmci.code.ValueUtil.isStackSlotValue;

import java.util.HashMap;
import java.util.Map;

import jdk.internal.jvmci.amd64.AMD64;
import jdk.internal.jvmci.amd64.AMD64Kind;
import jdk.internal.jvmci.code.Architecture;
import jdk.internal.jvmci.code.CallingConvention;
import jdk.internal.jvmci.code.CodeUtil;
import jdk.internal.jvmci.code.Register;
import jdk.internal.jvmci.code.RegisterConfig;
import jdk.internal.jvmci.code.RegisterValue;
import jdk.internal.jvmci.code.StackSlotValue;
import jdk.internal.jvmci.code.VirtualStackSlot;
import jdk.internal.jvmci.common.JVMCIError;
import jdk.internal.jvmci.meta.AllocatableValue;
import jdk.internal.jvmci.meta.Constant;
import jdk.internal.jvmci.meta.JavaConstant;
import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.LIRKind;
import jdk.internal.jvmci.meta.PlatformKind;
import jdk.internal.jvmci.meta.Value;

import com.oracle.graal.asm.NumUtil;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64MIOp;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64MOp;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64MROp;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMIOp;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64Shift;
import com.oracle.graal.asm.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize;
import com.oracle.graal.asm.amd64.AMD64Assembler.SSEOp;
import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.compiler.common.calc.FloatConvert;
import com.oracle.graal.compiler.common.spi.ForeignCallLinkage;
import com.oracle.graal.compiler.common.spi.LIRKindTool;
import com.oracle.graal.compiler.common.util.Util;
import com.oracle.graal.lir.ConstantValue;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LIRValueUtil;
import com.oracle.graal.lir.LabelRef;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.SwitchStrategy;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.amd64.AMD64AddressValue;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.FPDivRemOp;
import com.oracle.graal.lir.amd64.AMD64ArithmeticLIRGenerator;
import com.oracle.graal.lir.amd64.AMD64ArrayEqualsOp;
import com.oracle.graal.lir.amd64.AMD64Binary;
import com.oracle.graal.lir.amd64.AMD64BinaryConsumer;
import com.oracle.graal.lir.amd64.AMD64ByteSwapOp;
import com.oracle.graal.lir.amd64.AMD64Call;
import com.oracle.graal.lir.amd64.AMD64ClearRegisterOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.BranchOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.CondMoveOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.FloatBranchOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.FloatCondMoveOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.ReturnOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.StrategySwitchOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.TableSwitchOp;
import com.oracle.graal.lir.amd64.AMD64LIRInstruction;
import com.oracle.graal.lir.amd64.AMD64MathIntrinsicOp;
import com.oracle.graal.lir.amd64.AMD64Move;
import com.oracle.graal.lir.amd64.AMD64Move.AMD64PushPopStackMove;
import com.oracle.graal.lir.amd64.AMD64Move.AMD64StackMove;
import com.oracle.graal.lir.amd64.AMD64Move.CompareAndSwapOp;
import com.oracle.graal.lir.amd64.AMD64Move.LeaDataOp;
import com.oracle.graal.lir.amd64.AMD64Move.LeaOp;
import com.oracle.graal.lir.amd64.AMD64Move.MembarOp;
import com.oracle.graal.lir.amd64.AMD64Move.MoveFromConstOp;
import com.oracle.graal.lir.amd64.AMD64Move.MoveFromRegOp;
import com.oracle.graal.lir.amd64.AMD64Move.MoveToRegOp;
import com.oracle.graal.lir.amd64.AMD64Move.StackLeaOp;
import com.oracle.graal.lir.amd64.AMD64MulDivOp;
import com.oracle.graal.lir.amd64.AMD64ShiftOp;
import com.oracle.graal.lir.amd64.AMD64SignExtendOp;
import com.oracle.graal.lir.amd64.AMD64Unary;
import com.oracle.graal.lir.framemap.FrameMapBuilder;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGenerator;
import com.oracle.graal.lir.gen.SpillMoveFactoryBase;
import com.oracle.graal.phases.util.Providers;

/**
 * This class implements the AMD64 specific portion of the LIR generator.
 */
public abstract class AMD64LIRGenerator extends LIRGenerator implements AMD64ArithmeticLIRGenerator {

    private static final RegisterValue RCX_I = AMD64.rcx.asValue(LIRKind.value(AMD64Kind.DWORD));

    private AMD64SpillMoveFactory moveFactory;
    private Map<PlatformKind.Key, RegisterBackupPair> categorized;

    private static class RegisterBackupPair {
        public final Register register;
        public final StackSlotValue backupSlot;

        RegisterBackupPair(Register register, StackSlotValue backupSlot) {
            this.register = register;
            this.backupSlot = backupSlot;
        }
    }

    private class AMD64SpillMoveFactory extends SpillMoveFactoryBase {

        @Override
        protected LIRInstruction createMoveIntern(AllocatableValue result, Value input) {
            return AMD64LIRGenerator.this.createMove(result, input);
        }

        @Override
        protected LIRInstruction createStackMoveIntern(AllocatableValue result, AllocatableValue input) {
            return AMD64LIRGenerator.this.createStackMove(result, input);
        }

        @Override
        protected LIRInstruction createLoadIntern(AllocatableValue result, Constant input) {
            return AMD64LIRGenerator.this.createMoveConstant(result, input);
        }
    }

    public AMD64LIRGenerator(LIRKindTool lirKindTool, Providers providers, CallingConvention cc, LIRGenerationResult lirGenRes) {
        super(lirKindTool, providers, cc, lirGenRes);
    }

    public SpillMoveFactory getSpillMoveFactory() {
        if (moveFactory == null) {
            moveFactory = new AMD64SpillMoveFactory();
        }
        return moveFactory;
    }

    @Override
    public boolean canInlineConstant(JavaConstant c) {
        switch (c.getJavaKind()) {
            case Long:
                return NumUtil.isInt(c.asLong()) && !getCodeCache().needsDataPatch(c);
            case Object:
                return c.isNull();
            default:
                return true;
        }
    }

    /**
     * Checks whether the supplied constant can be used without loading it into a register for store
     * operations, i.e., on the right hand side of a memory access.
     *
     * @param c The constant to check.
     * @return True if the constant can be used directly, false if the constant needs to be in a
     *         register.
     */
    protected final boolean canStoreConstant(JavaConstant c) {
        // there is no immediate move of 64-bit constants on Intel
        switch (c.getJavaKind()) {
            case Long:
                return Util.isInt(c.asLong()) && !getCodeCache().needsDataPatch(c);
            case Double:
                return false;
            case Object:
                return c.isNull();
            default:
                return true;
        }
    }

    @Override
    protected JavaConstant zapValueForKind(PlatformKind kind) {
        long dead = 0xDEADDEADDEADDEADL;
        switch ((AMD64Kind) kind) {
            case BYTE:
                return JavaConstant.forByte((byte) dead);
            case WORD:
                return JavaConstant.forShort((short) dead);
            case DWORD:
                return JavaConstant.forInt((int) dead);
            case QWORD:
                return JavaConstant.forLong(dead);
            case SINGLE:
                return JavaConstant.forFloat(Float.intBitsToFloat((int) dead));
            default:
                // we don't support vector types, so just zap with double for all of them
                return JavaConstant.forDouble(Double.longBitsToDouble(dead));
        }
    }

    protected AMD64LIRInstruction createMove(AllocatableValue dst, Value src) {
        if (src instanceof AMD64AddressValue) {
            return new LeaOp(dst, (AMD64AddressValue) src);
        } else if (isJavaConstant(src)) {
            return createMoveConstant(dst, asJavaConstant(src));
        } else if (isRegister(src) || isStackSlotValue(dst)) {
            return new MoveFromRegOp((AMD64Kind) dst.getPlatformKind(), dst, (AllocatableValue) src);
        } else {
            return new MoveToRegOp((AMD64Kind) dst.getPlatformKind(), dst, (AllocatableValue) src);
        }
    }

    protected AMD64LIRInstruction createMoveConstant(AllocatableValue dst, Constant src) {
        return new MoveFromConstOp(dst, (JavaConstant) src);
    }

    protected LIRInstruction createStackMove(AllocatableValue result, AllocatableValue input) {
        AMD64Kind kind = (AMD64Kind) result.getPlatformKind();
        switch (kind.getSizeInBytes()) {
            case 2:
                return new AMD64PushPopStackMove(WORD, result, input);
            case 8:
                return new AMD64PushPopStackMove(QWORD, result, input);
            default:
                RegisterBackupPair backup = getScratchRegister(input.getPlatformKind());
                Register scratchRegister = backup.register;
                StackSlotValue backupSlot = backup.backupSlot;
                return createStackMove(result, input, scratchRegister, backupSlot);
        }
    }

    protected LIRInstruction createStackMove(AllocatableValue result, AllocatableValue input, Register scratchRegister, StackSlotValue backupSlot) {
        return new AMD64StackMove(result, input, scratchRegister, backupSlot);
    }

    protected RegisterBackupPair getScratchRegister(PlatformKind kind) {
        PlatformKind.Key key = kind.getKey();
        if (categorized == null) {
            categorized = new HashMap<>();
        } else if (categorized.containsKey(key)) {
            return categorized.get(key);
        }

        FrameMapBuilder frameMapBuilder = getResult().getFrameMapBuilder();
        RegisterConfig registerConfig = frameMapBuilder.getRegisterConfig();

        Register[] availableRegister = registerConfig.filterAllocatableRegisters(kind, registerConfig.getAllocatableRegisters());
        assert availableRegister != null && availableRegister.length > 1;
        Register scratchRegister = availableRegister[0];

        Architecture arch = frameMapBuilder.getCodeCache().getTarget().arch;
        LIRKind largestKind = LIRKind.value(arch.getLargestStorableKind(scratchRegister.getRegisterCategory()));
        VirtualStackSlot backupSlot = frameMapBuilder.allocateSpillSlot(largestKind);

        RegisterBackupPair value = new RegisterBackupPair(scratchRegister, backupSlot);
        categorized.put(key, value);

        return value;
    }

    @Override
    public void emitMove(AllocatableValue dst, Value src) {
        if (src instanceof ConstantValue) {
            emitMoveConstant(dst, ((ConstantValue) src).getConstant());
        } else {
            append(createMove(dst, src));
        }
    }

    @Override
    public void emitMoveConstant(AllocatableValue dst, Constant src) {
        append(createMoveConstant(dst, src));
    }

    public void emitData(AllocatableValue dst, byte[] data) {
        append(new LeaDataOp(dst, data));
    }

    public AMD64AddressValue asAddressValue(Value address) {
        if (address instanceof AMD64AddressValue) {
            return (AMD64AddressValue) address;
        } else {
            if (address instanceof JavaConstant) {
                long displacement = ((JavaConstant) address).asLong();
                if (NumUtil.isInt(displacement)) {
                    return new AMD64AddressValue(address.getLIRKind(), Value.ILLEGAL, (int) displacement);
                }
            }
            return new AMD64AddressValue(address.getLIRKind(), asAllocatable(address), 0);
        }
    }

    @Override
    public Variable emitAddress(StackSlotValue address) {
        Variable result = newVariable(LIRKind.value(target().arch.getWordKind()));
        append(new StackLeaOp(result, address));
        return result;
    }

    /**
     * The AMD64 backend only uses DWORD and QWORD values in registers because of a performance
     * penalty when accessing WORD or BYTE registers. This function converts small integer kinds to
     * DWORD.
     */
    @Override
    public LIRKind toRegisterKind(LIRKind kind) {
        switch ((AMD64Kind) kind.getPlatformKind()) {
            case BYTE:
            case WORD:
                return kind.changeType(AMD64Kind.DWORD);
            default:
                return kind;
        }
    }

    @Override
    public Variable emitLoad(LIRKind kind, Value address, LIRFrameState state) {
        AMD64AddressValue loadAddress = asAddressValue(address);
        Variable result = newVariable(toRegisterKind(kind));
        switch ((AMD64Kind) kind.getPlatformKind()) {
            case BYTE:
                append(new AMD64Unary.MemoryOp(MOVSXB, DWORD, result, loadAddress, state));
                break;
            case WORD:
                append(new AMD64Unary.MemoryOp(MOVSX, DWORD, result, loadAddress, state));
                break;
            case DWORD:
                append(new AMD64Unary.MemoryOp(MOV, DWORD, result, loadAddress, state));
                break;
            case QWORD:
                append(new AMD64Unary.MemoryOp(MOV, QWORD, result, loadAddress, state));
                break;
            case SINGLE:
                append(new AMD64Unary.MemoryOp(MOVSS, SS, result, loadAddress, state));
                break;
            case DOUBLE:
                append(new AMD64Unary.MemoryOp(MOVSD, SD, result, loadAddress, state));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return result;
    }

    protected void emitStoreConst(AMD64Kind kind, AMD64AddressValue address, ConstantValue value, LIRFrameState state) {
        JavaConstant c = value.getJavaConstant();
        if (c.isNull()) {
            assert kind == AMD64Kind.DWORD || kind == AMD64Kind.QWORD;
            OperandSize size = kind == AMD64Kind.DWORD ? DWORD : QWORD;
            append(new AMD64BinaryConsumer.MemoryConstOp(AMD64MIOp.MOV, size, address, 0, state));
        } else {
            AMD64MIOp op = AMD64MIOp.MOV;
            OperandSize size;
            long imm;

            switch (kind) {
                case BYTE:
                    op = AMD64MIOp.MOVB;
                    size = BYTE;
                    imm = c.asInt();
                    break;
                case WORD:
                    size = WORD;
                    imm = c.asInt();
                    break;
                case DWORD:
                    size = DWORD;
                    imm = c.asInt();
                    break;
                case QWORD:
                    size = QWORD;
                    imm = c.asLong();
                    break;
                case SINGLE:
                    size = DWORD;
                    imm = Float.floatToRawIntBits(c.asFloat());
                    break;
                case DOUBLE:
                    size = QWORD;
                    imm = Double.doubleToRawLongBits(c.asDouble());
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere("unexpected kind " + kind);
            }

            if (NumUtil.isInt(imm)) {
                append(new AMD64BinaryConsumer.MemoryConstOp(op, size, address, (int) imm, state));
            } else {
                emitStore(kind, address, asAllocatable(value), state);
            }
        }
    }

    protected void emitStore(AMD64Kind kind, AMD64AddressValue address, AllocatableValue value, LIRFrameState state) {
        switch (kind) {
            case BYTE:
                append(new AMD64BinaryConsumer.MemoryMROp(AMD64MROp.MOVB, BYTE, address, value, state));
                break;
            case WORD:
                append(new AMD64BinaryConsumer.MemoryMROp(AMD64MROp.MOV, WORD, address, value, state));
                break;
            case DWORD:
                append(new AMD64BinaryConsumer.MemoryMROp(AMD64MROp.MOV, DWORD, address, value, state));
                break;
            case QWORD:
                append(new AMD64BinaryConsumer.MemoryMROp(AMD64MROp.MOV, QWORD, address, value, state));
                break;
            case SINGLE:
                append(new AMD64BinaryConsumer.MemoryMROp(AMD64MROp.MOVSS, SS, address, value, state));
                break;
            case DOUBLE:
                append(new AMD64BinaryConsumer.MemoryMROp(AMD64MROp.MOVSD, SD, address, value, state));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public void emitStore(LIRKind lirKind, Value address, Value input, LIRFrameState state) {
        AMD64AddressValue storeAddress = asAddressValue(address);
        AMD64Kind kind = (AMD64Kind) lirKind.getPlatformKind();
        if (isJavaConstant(input)) {
            emitStoreConst(kind, storeAddress, asConstantValue(input), state);
        } else {
            emitStore(kind, storeAddress, asAllocatable(input), state);
        }
    }

    @Override
    public Variable emitCompareAndSwap(Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue) {
        LIRKind kind = newValue.getLIRKind();
        assert kind.equals(expectedValue.getLIRKind());
        AMD64Kind memKind = (AMD64Kind) kind.getPlatformKind();

        AMD64AddressValue addressValue = asAddressValue(address);
        RegisterValue raxRes = AMD64.rax.asValue(kind);
        emitMove(raxRes, expectedValue);
        append(new CompareAndSwapOp(memKind, raxRes, addressValue, raxRes, asAllocatable(newValue)));

        assert trueValue.getLIRKind().equals(falseValue.getLIRKind());
        Variable result = newVariable(trueValue.getLIRKind());
        append(new CondMoveOp(result, Condition.EQ, asAllocatable(trueValue), falseValue));
        return result;
    }

    @Override
    public Value emitAtomicReadAndAdd(Value address, Value delta) {
        LIRKind kind = delta.getLIRKind();
        Variable result = newVariable(kind);
        AMD64AddressValue addressValue = asAddressValue(address);
        append(new AMD64Move.AtomicReadAndAddOp((AMD64Kind) kind.getPlatformKind(), result, addressValue, asAllocatable(delta)));
        return result;
    }

    @Override
    public Value emitAtomicReadAndWrite(Value address, Value newValue) {
        LIRKind kind = newValue.getLIRKind();
        Variable result = newVariable(kind);
        AMD64AddressValue addressValue = asAddressValue(address);
        append(new AMD64Move.AtomicReadAndWriteOp((AMD64Kind) kind.getPlatformKind(), result, addressValue, asAllocatable(newValue)));
        return result;
    }

    @Override
    public void emitNullCheck(Value address, LIRFrameState state) {
        append(new AMD64Move.NullCheckOp(asAddressValue(address), state));
    }

    @Override
    public void emitJump(LabelRef label) {
        assert label != null;
        append(new JumpOp(label));
    }

    @Override
    public void emitCompareBranch(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueLabel, LabelRef falseLabel, double trueLabelProbability) {
        boolean mirrored = emitCompare(cmpKind, left, right);
        Condition finalCondition = mirrored ? cond.mirror() : cond;
        if (cmpKind == AMD64Kind.SINGLE || cmpKind == AMD64Kind.DOUBLE) {
            append(new FloatBranchOp(finalCondition, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability));
        } else {
            append(new BranchOp(finalCondition, trueLabel, falseLabel, trueLabelProbability));
        }
    }

    public void emitCompareBranchMemory(AMD64Kind cmpKind, Value left, AMD64AddressValue right, LIRFrameState state, Condition cond, boolean unorderedIsTrue, LabelRef trueLabel, LabelRef falseLabel,
                    double trueLabelProbability) {
        boolean mirrored = emitCompareMemory(cmpKind, left, right, state);
        Condition finalCondition = mirrored ? cond.mirror() : cond;
        if (cmpKind.isXMM()) {
            append(new FloatBranchOp(finalCondition, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability));
        } else {
            append(new BranchOp(finalCondition, trueLabel, falseLabel, trueLabelProbability));
        }
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, LIRKind cmpLIRKind, double overflowProbability) {
        append(new BranchOp(ConditionFlag.Overflow, overflow, noOverflow, overflowProbability));
    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
        emitIntegerTest(left, right);
        append(new BranchOp(Condition.EQ, trueDestination, falseDestination, trueDestinationProbability));
    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        boolean mirrored = emitCompare(cmpKind, left, right);
        Condition finalCondition = mirrored ? cond.mirror() : cond;

        Variable result = newVariable(trueValue.getLIRKind());
        if (cmpKind == AMD64Kind.SINGLE || cmpKind == AMD64Kind.DOUBLE) {
            append(new FloatCondMoveOp(result, finalCondition, unorderedIsTrue, load(trueValue), load(falseValue)));
        } else {
            append(new CondMoveOp(result, finalCondition, load(trueValue), loadNonConst(falseValue)));
        }
        return result;
    }

    @Override
    public Variable emitIntegerTestMove(Value left, Value right, Value trueValue, Value falseValue) {
        emitIntegerTest(left, right);
        Variable result = newVariable(trueValue.getLIRKind());
        append(new CondMoveOp(result, Condition.EQ, load(trueValue), loadNonConst(falseValue)));
        return result;
    }

    private void emitIntegerTest(Value a, Value b) {
        assert ((AMD64Kind) a.getPlatformKind()).isInteger();
        OperandSize size = a.getPlatformKind() == AMD64Kind.QWORD ? QWORD : DWORD;
        if (isJavaConstant(b) && NumUtil.is32bit(asJavaConstant(b).asLong())) {
            append(new AMD64BinaryConsumer.ConstOp(AMD64MIOp.TEST, size, asAllocatable(a), (int) asJavaConstant(b).asLong()));
        } else if (isJavaConstant(a) && NumUtil.is32bit(asJavaConstant(a).asLong())) {
            append(new AMD64BinaryConsumer.ConstOp(AMD64MIOp.TEST, size, asAllocatable(b), (int) asJavaConstant(a).asLong()));
        } else if (isAllocatableValue(b)) {
            append(new AMD64BinaryConsumer.Op(AMD64RMOp.TEST, size, asAllocatable(b), asAllocatable(a)));
        } else {
            append(new AMD64BinaryConsumer.Op(AMD64RMOp.TEST, size, asAllocatable(a), asAllocatable(b)));
        }
    }

    protected void emitCompareOp(PlatformKind cmpKind, Variable left, Value right) {
        OperandSize size;
        switch ((AMD64Kind) cmpKind) {
            case BYTE:
                size = BYTE;
                break;
            case WORD:
                size = WORD;
                break;
            case DWORD:
                size = DWORD;
                break;
            case QWORD:
                size = QWORD;
                break;
            case SINGLE:
                append(new AMD64BinaryConsumer.Op(SSEOp.UCOMIS, PS, left, asAllocatable(right)));
                return;
            case DOUBLE:
                append(new AMD64BinaryConsumer.Op(SSEOp.UCOMIS, PD, left, asAllocatable(right)));
                return;
            default:
                throw JVMCIError.shouldNotReachHere("unexpected kind: " + cmpKind);
        }

        if (isJavaConstant(right)) {
            JavaConstant c = asJavaConstant(right);
            if (c.isDefaultForKind()) {
                AMD64RMOp op = size == BYTE ? TESTB : TEST;
                append(new AMD64BinaryConsumer.Op(op, size, left, left));
                return;
            } else if (NumUtil.is32bit(c.asLong())) {
                append(new AMD64BinaryConsumer.ConstOp(CMP, size, left, (int) c.asLong()));
                return;
            }
        }

        AMD64RMOp op = CMP.getRMOpcode(size);
        append(new AMD64BinaryConsumer.Op(op, size, left, asAllocatable(right)));
    }

    /**
     * This method emits the compare against memory instruction, and may reorder the operands. It
     * returns true if it did so.
     *
     * @param b the right operand of the comparison
     * @return true if the left and right operands were switched, false otherwise
     */
    private boolean emitCompareMemory(AMD64Kind cmpKind, Value a, AMD64AddressValue b, LIRFrameState state) {
        OperandSize size;
        switch (cmpKind) {
            case BYTE:
                size = OperandSize.BYTE;
                break;
            case WORD:
                size = OperandSize.WORD;
                break;
            case DWORD:
                size = OperandSize.DWORD;
                break;
            case QWORD:
                size = OperandSize.QWORD;
                break;
            case SINGLE:
                append(new AMD64BinaryConsumer.MemoryRMOp(SSEOp.UCOMIS, PS, asAllocatable(a), b, state));
                return false;
            case DOUBLE:
                append(new AMD64BinaryConsumer.MemoryRMOp(SSEOp.UCOMIS, PD, asAllocatable(a), b, state));
                return false;
            default:
                throw JVMCIError.shouldNotReachHere("unexpected kind: " + cmpKind);
        }

        if (isJavaConstant(a)) {
            return emitCompareMemoryConOp(size, asConstantValue(a), b, state);
        } else {
            return emitCompareRegMemoryOp(size, asAllocatable(a), b, state);
        }
    }

    protected boolean emitCompareMemoryConOp(OperandSize size, ConstantValue a, AMD64AddressValue b, LIRFrameState state) {
        long value = a.getJavaConstant().asLong();
        if (NumUtil.is32bit(value)) {
            append(new AMD64BinaryConsumer.MemoryConstOp(CMP, size, b, (int) value, state));
            return true;
        } else {
            return emitCompareRegMemoryOp(size, asAllocatable(a), b, state);
        }
    }

    private boolean emitCompareRegMemoryOp(OperandSize size, AllocatableValue a, AMD64AddressValue b, LIRFrameState state) {
        AMD64RMOp op = CMP.getRMOpcode(size);
        append(new AMD64BinaryConsumer.MemoryRMOp(op, size, a, b, state));
        return false;
    }

    /**
     * This method emits the compare instruction, and may reorder the operands. It returns true if
     * it did so.
     *
     * @param a the left operand of the comparison
     * @param b the right operand of the comparison
     * @return true if the left and right operands were switched, false otherwise
     */
    private boolean emitCompare(PlatformKind cmpKind, Value a, Value b) {
        Variable left;
        Value right;
        boolean mirrored;
        if (LIRValueUtil.isVariable(b)) {
            left = load(b);
            right = loadNonConst(a);
            mirrored = true;
        } else {
            left = load(a);
            right = loadNonConst(b);
            mirrored = false;
        }
        emitCompareOp(cmpKind, left, right);
        return mirrored;
    }

    @Override
    public Variable emitNegate(Value inputVal) {
        AllocatableValue input = asAllocatable(inputVal);
        Variable result = newVariable(LIRKind.combine(input));
        switch ((AMD64Kind) input.getPlatformKind()) {
            case DWORD:
                append(new AMD64Unary.MOp(NEG, DWORD, result, input));
                break;
            case QWORD:
                append(new AMD64Unary.MOp(NEG, QWORD, result, input));
                break;
            case SINGLE:
                append(new AMD64Binary.DataOp(SSEOp.XOR, PS, result, input, JavaConstant.forFloat(Float.intBitsToFloat(0x80000000)), 16));
                break;
            case DOUBLE:
                append(new AMD64Binary.DataOp(SSEOp.XOR, PD, result, input, JavaConstant.forDouble(Double.longBitsToDouble(0x8000000000000000L)), 16));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitNot(Value inputVal) {
        AllocatableValue input = asAllocatable(inputVal);
        Variable result = newVariable(LIRKind.combine(input));
        switch ((AMD64Kind) input.getPlatformKind()) {
            case DWORD:
                append(new AMD64Unary.MOp(NOT, DWORD, result, input));
                break;
            case QWORD:
                append(new AMD64Unary.MOp(NOT, QWORD, result, input));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return result;
    }

    private Variable emitBinary(LIRKind resultKind, AMD64BinaryArithmetic op, OperandSize size, boolean commutative, Value a, Value b, boolean setFlags) {
        if (isJavaConstant(b)) {
            return emitBinaryConst(resultKind, op, size, commutative, asAllocatable(a), asConstantValue(b), setFlags);
        } else if (commutative && isJavaConstant(a)) {
            return emitBinaryConst(resultKind, op, size, commutative, asAllocatable(b), asConstantValue(a), setFlags);
        } else {
            return emitBinaryVar(resultKind, op.getRMOpcode(size), size, commutative, asAllocatable(a), asAllocatable(b));
        }
    }

    private Variable emitBinary(LIRKind resultKind, AMD64RMOp op, OperandSize size, boolean commutative, Value a, Value b) {
        if (isJavaConstant(b)) {
            return emitBinaryConst(resultKind, op, size, asAllocatable(a), asJavaConstant(b));
        } else if (commutative && isJavaConstant(a)) {
            return emitBinaryConst(resultKind, op, size, asAllocatable(b), asJavaConstant(a));
        } else {
            return emitBinaryVar(resultKind, op, size, commutative, asAllocatable(a), asAllocatable(b));
        }
    }

    private Variable emitBinaryConst(LIRKind resultKind, AMD64BinaryArithmetic op, OperandSize size, boolean commutative, AllocatableValue a, ConstantValue b, boolean setFlags) {
        long value = b.getJavaConstant().asLong();
        if (NumUtil.isInt(value)) {
            Variable result = newVariable(resultKind);
            int constant = (int) value;

            if (!setFlags) {
                AMD64MOp mop = getMOp(op, constant);
                if (mop != null) {
                    append(new AMD64Unary.MOp(mop, size, result, a));
                    return result;
                }
            }

            append(new AMD64Binary.ConstOp(op, size, result, a, constant));
            return result;
        } else {
            return emitBinaryVar(resultKind, op.getRMOpcode(size), size, commutative, a, asAllocatable(b));
        }
    }

    private static AMD64MOp getMOp(AMD64BinaryArithmetic op, int constant) {
        if (constant == 1) {
            if (op.equals(AMD64BinaryArithmetic.ADD)) {
                return AMD64MOp.INC;
            }
            if (op.equals(AMD64BinaryArithmetic.SUB)) {
                return AMD64MOp.DEC;
            }
        } else if (constant == -1) {
            if (op.equals(AMD64BinaryArithmetic.ADD)) {
                return AMD64MOp.DEC;
            }
            if (op.equals(AMD64BinaryArithmetic.SUB)) {
                return AMD64MOp.INC;
            }
        }
        return null;
    }

    private Variable emitBinaryConst(LIRKind resultKind, AMD64RMOp op, OperandSize size, AllocatableValue a, JavaConstant b) {
        Variable result = newVariable(resultKind);
        append(new AMD64Binary.DataOp(op, size, result, a, b));
        return result;
    }

    private Variable emitBinaryVar(LIRKind resultKind, AMD64RMOp op, OperandSize size, boolean commutative, AllocatableValue a, AllocatableValue b) {
        Variable result = newVariable(resultKind);
        if (commutative) {
            append(new AMD64Binary.CommutativeOp(op, size, result, a, b));
        } else {
            append(new AMD64Binary.Op(op, size, result, a, b));
        }
        return result;
    }

    @Override
    protected boolean isNumericInteger(PlatformKind kind) {
        return ((AMD64Kind) kind).isInteger();
    }

    @Override
    public Variable emitAdd(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitBinary(resultKind, ADD, DWORD, true, a, b, setFlags);
            case QWORD:
                return emitBinary(resultKind, ADD, QWORD, true, a, b, setFlags);
            case SINGLE:
                return emitBinary(resultKind, SSEOp.ADD, SS, true, a, b);
            case DOUBLE:
                return emitBinary(resultKind, SSEOp.ADD, SD, true, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitSub(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitBinary(resultKind, SUB, DWORD, false, a, b, setFlags);
            case QWORD:
                return emitBinary(resultKind, SUB, QWORD, false, a, b, setFlags);
            case SINGLE:
                return emitBinary(resultKind, SSEOp.SUB, SS, false, a, b);
            case DOUBLE:
                return emitBinary(resultKind, SSEOp.SUB, SD, false, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private Variable emitIMULConst(OperandSize size, AllocatableValue a, ConstantValue b) {
        long value = b.getJavaConstant().asLong();
        if (NumUtil.isInt(value)) {
            int imm = (int) value;
            AMD64RMIOp op;
            if (NumUtil.isByte(imm)) {
                op = AMD64RMIOp.IMUL_SX;
            } else {
                op = AMD64RMIOp.IMUL;
            }

            Variable ret = newVariable(LIRKind.combine(a, b));
            append(new AMD64Binary.RMIOp(op, size, ret, a, imm));
            return ret;
        } else {
            return emitBinaryVar(LIRKind.combine(a, b), AMD64RMOp.IMUL, size, true, a, asAllocatable(b));
        }
    }

    private Variable emitIMUL(OperandSize size, Value a, Value b) {
        if (isJavaConstant(b)) {
            return emitIMULConst(size, asAllocatable(a), asConstantValue(b));
        } else if (isJavaConstant(a)) {
            return emitIMULConst(size, asAllocatable(b), asConstantValue(a));
        } else {
            return emitBinaryVar(LIRKind.combine(a, b), AMD64RMOp.IMUL, size, true, asAllocatable(a), asAllocatable(b));
        }
    }

    @Override
    public Variable emitMul(Value a, Value b, boolean setFlags) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitIMUL(DWORD, a, b);
            case QWORD:
                return emitIMUL(QWORD, a, b);
            case SINGLE:
                return emitBinary(LIRKind.combine(a, b), SSEOp.MUL, SS, true, a, b);
            case DOUBLE:
                return emitBinary(LIRKind.combine(a, b), SSEOp.MUL, SD, true, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private RegisterValue moveToReg(Register reg, Value v) {
        RegisterValue ret = reg.asValue(v.getLIRKind());
        emitMove(ret, v);
        return ret;
    }

    private Value emitMulHigh(AMD64MOp opcode, OperandSize size, Value a, Value b) {
        AMD64MulDivOp mulHigh = append(new AMD64MulDivOp(opcode, size, LIRKind.combine(a, b), moveToReg(AMD64.rax, a), asAllocatable(b)));
        return emitMove(mulHigh.getHighResult());
    }

    @Override
    public Value emitMulHigh(Value a, Value b) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitMulHigh(AMD64MOp.IMUL, DWORD, a, b);
            case QWORD:
                return emitMulHigh(AMD64MOp.IMUL, QWORD, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Value emitUMulHigh(Value a, Value b) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitMulHigh(AMD64MOp.MUL, DWORD, a, b);
            case QWORD:
                return emitMulHigh(AMD64MOp.MUL, QWORD, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    public Value emitBinaryMemory(AMD64RMOp op, OperandSize size, AllocatableValue a, AMD64AddressValue location, LIRFrameState state) {
        Variable result = newVariable(LIRKind.combine(a));
        append(new AMD64Binary.MemoryOp(op, size, result, a, location, state));
        return result;
    }

    protected Value emitConvertMemoryOp(PlatformKind kind, AMD64RMOp op, OperandSize size, AMD64AddressValue address, LIRFrameState state) {
        Variable result = newVariable(LIRKind.value(kind));
        append(new AMD64Unary.MemoryOp(op, size, result, address, state));
        return result;
    }

    protected Value emitZeroExtendMemory(AMD64Kind memoryKind, int resultBits, AMD64AddressValue address, LIRFrameState state) {
        // Issue a zero extending load of the proper bit size and set the result to
        // the proper kind.
        Variable result = newVariable(LIRKind.value(resultBits == 32 ? AMD64Kind.DWORD : AMD64Kind.QWORD));
        switch (memoryKind) {
            case BYTE:
                append(new AMD64Unary.MemoryOp(MOVZXB, DWORD, result, address, state));
                break;
            case WORD:
                append(new AMD64Unary.MemoryOp(MOVZX, DWORD, result, address, state));
                break;
            case DWORD:
                append(new AMD64Unary.MemoryOp(MOV, DWORD, result, address, state));
                break;
            case QWORD:
                append(new AMD64Unary.MemoryOp(MOV, QWORD, result, address, state));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return result;
    }

    private AMD64MulDivOp emitIDIV(OperandSize size, Value a, Value b, LIRFrameState state) {
        LIRKind kind = LIRKind.combine(a, b);

        AMD64SignExtendOp sx = append(new AMD64SignExtendOp(size, kind, moveToReg(AMD64.rax, a)));
        return append(new AMD64MulDivOp(AMD64MOp.IDIV, size, kind, sx.getHighResult(), sx.getLowResult(), asAllocatable(b), state));
    }

    private AMD64MulDivOp emitDIV(OperandSize size, Value a, Value b, LIRFrameState state) {
        LIRKind kind = LIRKind.combine(a, b);

        RegisterValue rax = moveToReg(AMD64.rax, a);
        RegisterValue rdx = AMD64.rdx.asValue(kind);
        append(new AMD64ClearRegisterOp(size, rdx));
        return append(new AMD64MulDivOp(AMD64MOp.DIV, size, kind, rdx, rax, asAllocatable(b), state));
    }

    public Value[] emitIntegerDivRem(Value a, Value b, LIRFrameState state) {
        AMD64MulDivOp op;
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                op = emitIDIV(DWORD, a, b, state);
                break;
            case QWORD:
                op = emitIDIV(QWORD, a, b, state);
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return new Value[]{emitMove(op.getQuotient()), emitMove(op.getRemainder())};
    }

    @Override
    public Value emitDiv(Value a, Value b, LIRFrameState state) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                AMD64MulDivOp op = emitIDIV(DWORD, a, b, state);
                return emitMove(op.getQuotient());
            case QWORD:
                AMD64MulDivOp lop = emitIDIV(QWORD, a, b, state);
                return emitMove(lop.getQuotient());
            case SINGLE:
                return emitBinary(LIRKind.combine(a, b), SSEOp.DIV, SS, false, a, b);
            case DOUBLE:
                return emitBinary(LIRKind.combine(a, b), SSEOp.DIV, SD, false, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Value emitRem(Value a, Value b, LIRFrameState state) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                AMD64MulDivOp op = emitIDIV(DWORD, a, b, state);
                return emitMove(op.getRemainder());
            case QWORD:
                AMD64MulDivOp lop = emitIDIV(QWORD, a, b, state);
                return emitMove(lop.getRemainder());
            case SINGLE: {
                Variable result = newVariable(LIRKind.combine(a, b));
                append(new FPDivRemOp(FREM, result, load(a), load(b)));
                return result;
            }
            case DOUBLE: {
                Variable result = newVariable(LIRKind.combine(a, b));
                append(new FPDivRemOp(DREM, result, load(a), load(b)));
                return result;
            }
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitUDiv(Value a, Value b, LIRFrameState state) {
        AMD64MulDivOp op;
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                op = emitDIV(DWORD, a, b, state);
                break;
            case QWORD:
                op = emitDIV(QWORD, a, b, state);
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return emitMove(op.getQuotient());
    }

    @Override
    public Variable emitURem(Value a, Value b, LIRFrameState state) {
        AMD64MulDivOp op;
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                op = emitDIV(DWORD, a, b, state);
                break;
            case QWORD:
                op = emitDIV(QWORD, a, b, state);
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return emitMove(op.getRemainder());
    }

    @Override
    public Variable emitAnd(Value a, Value b) {
        LIRKind resultKind = LIRKind.combine(a, b);
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitBinary(resultKind, AND, DWORD, true, a, b, false);
            case QWORD:
                return emitBinary(resultKind, AND, QWORD, true, a, b, false);
            case SINGLE:
                return emitBinary(resultKind, SSEOp.AND, PS, true, a, b);
            case DOUBLE:
                return emitBinary(resultKind, SSEOp.AND, PD, true, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitOr(Value a, Value b) {
        LIRKind resultKind = LIRKind.combine(a, b);
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitBinary(resultKind, OR, DWORD, true, a, b, false);
            case QWORD:
                return emitBinary(resultKind, OR, QWORD, true, a, b, false);
            case SINGLE:
                return emitBinary(resultKind, SSEOp.OR, PS, true, a, b);
            case DOUBLE:
                return emitBinary(resultKind, SSEOp.OR, PD, true, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitXor(Value a, Value b) {
        LIRKind resultKind = LIRKind.combine(a, b);
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitBinary(resultKind, XOR, DWORD, true, a, b, false);
            case QWORD:
                return emitBinary(resultKind, XOR, QWORD, true, a, b, false);
            case SINGLE:
                return emitBinary(resultKind, SSEOp.XOR, PS, true, a, b);
            case DOUBLE:
                return emitBinary(resultKind, SSEOp.XOR, PD, true, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private Variable emitShift(AMD64Shift op, OperandSize size, Value a, Value b) {
        Variable result = newVariable(LIRKind.combine(a, b).changeType(a.getPlatformKind()));
        AllocatableValue input = asAllocatable(a);
        if (isJavaConstant(b)) {
            JavaConstant c = asJavaConstant(b);
            if (c.asLong() == 1) {
                append(new AMD64Unary.MOp(op.m1Op, size, result, input));
            } else {
                /*
                 * c is implicitly masked to 5 or 6 bits by the CPU, so casting it to (int) is
                 * always correct, even without the NumUtil.is32bit() test.
                 */
                append(new AMD64Binary.ConstOp(op.miOp, size, result, input, (int) c.asLong()));
            }
        } else {
            emitMove(RCX_I, b);
            append(new AMD64ShiftOp(op.mcOp, size, result, input, RCX_I));
        }
        return result;
    }

    @Override
    public Variable emitShl(Value a, Value b) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitShift(SHL, DWORD, a, b);
            case QWORD:
                return emitShift(SHL, QWORD, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitShr(Value a, Value b) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitShift(SAR, DWORD, a, b);
            case QWORD:
                return emitShift(SAR, QWORD, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitUShr(Value a, Value b) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitShift(SHR, DWORD, a, b);
            case QWORD:
                return emitShift(SHR, QWORD, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    public Variable emitRol(Value a, Value b) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitShift(ROL, DWORD, a, b);
            case QWORD:
                return emitShift(ROL, QWORD, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    public Variable emitRor(Value a, Value b) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitShift(ROR, DWORD, a, b);
            case QWORD:
                return emitShift(ROR, QWORD, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private AllocatableValue emitConvertOp(LIRKind kind, AMD64RMOp op, OperandSize size, Value input) {
        Variable result = newVariable(kind);
        append(new AMD64Unary.RMOp(op, size, result, asAllocatable(input)));
        return result;
    }

    private AllocatableValue emitConvertOp(LIRKind kind, AMD64MROp op, OperandSize size, Value input) {
        Variable result = newVariable(kind);
        append(new AMD64Unary.MROp(op, size, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitReinterpret(LIRKind to, Value inputVal) {
        LIRKind from = inputVal.getLIRKind();
        if (to.equals(from)) {
            return inputVal;
        }

        AllocatableValue input = asAllocatable(inputVal);
        /*
         * Conversions between integer to floating point types require moves between CPU and FPU
         * registers.
         */
        AMD64Kind fromKind = (AMD64Kind) from.getPlatformKind();
        switch ((AMD64Kind) to.getPlatformKind()) {
            case DWORD:
                switch (fromKind) {
                    case SINGLE:
                        return emitConvertOp(to, AMD64MROp.MOVD, DWORD, input);
                }
                break;
            case QWORD:
                switch (fromKind) {
                    case DOUBLE:
                        return emitConvertOp(to, AMD64MROp.MOVQ, QWORD, input);
                }
                break;
            case SINGLE:
                switch (fromKind) {
                    case DWORD:
                        return emitConvertOp(to, AMD64RMOp.MOVD, DWORD, input);
                }
                break;
            case DOUBLE:
                switch (fromKind) {
                    case QWORD:
                        return emitConvertOp(to, AMD64RMOp.MOVQ, QWORD, input);
                }
                break;
        }
        throw JVMCIError.shouldNotReachHere();
    }

    public Value emitFloatConvert(FloatConvert op, Value input) {
        switch (op) {
            case D2F:
                return emitConvertOp(LIRKind.combine(input).changeType(AMD64Kind.SINGLE), SSEOp.CVTSD2SS, SD, input);
            case D2I:
                return emitConvertOp(LIRKind.combine(input).changeType(AMD64Kind.DWORD), SSEOp.CVTTSD2SI, DWORD, input);
            case D2L:
                return emitConvertOp(LIRKind.combine(input).changeType(AMD64Kind.QWORD), SSEOp.CVTTSD2SI, QWORD, input);
            case F2D:
                return emitConvertOp(LIRKind.combine(input).changeType(AMD64Kind.DOUBLE), SSEOp.CVTSS2SD, SS, input);
            case F2I:
                return emitConvertOp(LIRKind.combine(input).changeType(AMD64Kind.DWORD), SSEOp.CVTTSS2SI, DWORD, input);
            case F2L:
                return emitConvertOp(LIRKind.combine(input).changeType(AMD64Kind.QWORD), SSEOp.CVTTSS2SI, QWORD, input);
            case I2D:
                return emitConvertOp(LIRKind.combine(input).changeType(AMD64Kind.DOUBLE), SSEOp.CVTSI2SD, DWORD, input);
            case I2F:
                return emitConvertOp(LIRKind.combine(input).changeType(AMD64Kind.SINGLE), SSEOp.CVTSI2SS, DWORD, input);
            case L2D:
                return emitConvertOp(LIRKind.combine(input).changeType(AMD64Kind.DOUBLE), SSEOp.CVTSI2SD, QWORD, input);
            case L2F:
                return emitConvertOp(LIRKind.combine(input).changeType(AMD64Kind.SINGLE), SSEOp.CVTSI2SS, QWORD, input);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Value emitNarrow(Value inputVal, int bits) {
        if (inputVal.getPlatformKind() == AMD64Kind.QWORD && bits <= 32) {
            // TODO make it possible to reinterpret Long as Int in LIR without move
            return emitConvertOp(LIRKind.combine(inputVal).changeType(AMD64Kind.DWORD), AMD64RMOp.MOV, DWORD, inputVal);
        } else {
            return inputVal;
        }
    }

    @Override
    public Value emitSignExtend(Value inputVal, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        if (fromBits == toBits) {
            return inputVal;
        } else if (toBits > 32) {
            // sign extend to 64 bits
            switch (fromBits) {
                case 8:
                    return emitConvertOp(LIRKind.combine(inputVal).changeType(AMD64Kind.QWORD), MOVSXB, QWORD, inputVal);
                case 16:
                    return emitConvertOp(LIRKind.combine(inputVal).changeType(AMD64Kind.QWORD), MOVSX, QWORD, inputVal);
                case 32:
                    return emitConvertOp(LIRKind.combine(inputVal).changeType(AMD64Kind.QWORD), MOVSXD, QWORD, inputVal);
                default:
                    throw JVMCIError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
            }
        } else {
            // sign extend to 32 bits (smaller values are internally represented as 32 bit values)
            switch (fromBits) {
                case 8:
                    return emitConvertOp(LIRKind.combine(inputVal).changeType(AMD64Kind.DWORD), MOVSXB, DWORD, inputVal);
                case 16:
                    return emitConvertOp(LIRKind.combine(inputVal).changeType(AMD64Kind.DWORD), MOVSX, DWORD, inputVal);
                case 32:
                    return inputVal;
                default:
                    throw JVMCIError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
            }
        }
    }

    @Override
    public Value emitZeroExtend(Value inputVal, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        if (fromBits == toBits) {
            return inputVal;
        } else if (fromBits > 32) {
            assert inputVal.getPlatformKind() == AMD64Kind.QWORD;
            Variable result = newVariable(LIRKind.combine(inputVal));
            long mask = CodeUtil.mask(fromBits);
            append(new AMD64Binary.DataOp(AND.getRMOpcode(QWORD), QWORD, result, asAllocatable(inputVal), JavaConstant.forLong(mask)));
            return result;
        } else {
            LIRKind resultKind = LIRKind.combine(inputVal);
            if (toBits > 32) {
                resultKind = resultKind.changeType(AMD64Kind.QWORD);
            } else {
                resultKind = resultKind.changeType(AMD64Kind.DWORD);
            }

            /*
             * Always emit DWORD operations, even if the resultKind is Long. On AMD64, all DWORD
             * operations implicitly set the upper half of the register to 0, which is what we want
             * anyway. Compared to the QWORD oparations, the encoding of the DWORD operations is
             * sometimes one byte shorter.
             */
            switch (fromBits) {
                case 8:
                    return emitConvertOp(resultKind, MOVZXB, DWORD, inputVal);
                case 16:
                    return emitConvertOp(resultKind, MOVZX, DWORD, inputVal);
                case 32:
                    return emitConvertOp(resultKind, MOV, DWORD, inputVal);
            }

            // odd bit count, fall back on manual masking
            Variable result = newVariable(resultKind);
            JavaConstant mask;
            if (toBits > 32) {
                mask = JavaConstant.forLong(CodeUtil.mask(fromBits));
            } else {
                mask = JavaConstant.forInt((int) CodeUtil.mask(fromBits));
            }
            append(new AMD64Binary.DataOp(AND.getRMOpcode(DWORD), DWORD, result, asAllocatable(inputVal), mask));
            return result;
        }
    }

    @Override
    public void emitMembar(int barriers) {
        int necessaryBarriers = target().arch.requiredBarriers(barriers);
        if (target().isMP && necessaryBarriers != 0) {
            append(new MembarOp(necessaryBarriers));
        }
    }

    public abstract void emitCCall(long address, CallingConvention nativeCallingConvention, Value[] args, int numberOfFloatingPointArguments);

    @Override
    protected void emitForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        long maxOffset = linkage.getMaxCallTargetOffset();
        if (maxOffset != (int) maxOffset) {
            append(new AMD64Call.DirectFarForeignCallOp(linkage, result, arguments, temps, info));
        } else {
            append(new AMD64Call.DirectNearForeignCallOp(linkage, result, arguments, temps, info));
        }
    }

    @Override
    public Variable emitBitCount(Value value) {
        Variable result = newVariable(LIRKind.combine(value).changeType(AMD64Kind.DWORD));
        assert ((AMD64Kind) value.getPlatformKind()).isInteger();
        if (value.getPlatformKind() == AMD64Kind.QWORD) {
            append(new AMD64Unary.RMOp(POPCNT, QWORD, result, asAllocatable(value)));
        } else {
            append(new AMD64Unary.RMOp(POPCNT, DWORD, result, asAllocatable(value)));
        }
        return result;
    }

    @Override
    public Variable emitBitScanForward(Value value) {
        Variable result = newVariable(LIRKind.combine(value).changeType(AMD64Kind.DWORD));
        append(new AMD64Unary.RMOp(BSF, QWORD, result, asAllocatable(value)));
        return result;
    }

    @Override
    public Variable emitBitScanReverse(Value value) {
        Variable result = newVariable(LIRKind.combine(value).changeType(AMD64Kind.DWORD));
        assert ((AMD64Kind) value.getPlatformKind()).isInteger();
        if (value.getPlatformKind() == AMD64Kind.QWORD) {
            append(new AMD64Unary.RMOp(BSR, QWORD, result, asAllocatable(value)));
        } else {
            append(new AMD64Unary.RMOp(BSR, DWORD, result, asAllocatable(value)));
        }
        return result;
    }

    public Value emitCountLeadingZeros(Value value) {
        Variable result = newVariable(LIRKind.combine(value).changeType(AMD64Kind.DWORD));
        assert ((AMD64Kind) value.getPlatformKind()).isInteger();
        if (value.getPlatformKind() == AMD64Kind.QWORD) {
            append(new AMD64Unary.RMOp(LZCNT, QWORD, result, asAllocatable(value)));
        } else {
            append(new AMD64Unary.RMOp(LZCNT, DWORD, result, asAllocatable(value)));
        }
        return result;
    }

    public Value emitCountTrailingZeros(Value value) {
        Variable result = newVariable(LIRKind.combine(value).changeType(AMD64Kind.DWORD));
        assert ((AMD64Kind) value.getPlatformKind()).isInteger();
        if (value.getPlatformKind() == AMD64Kind.QWORD) {
            append(new AMD64Unary.RMOp(TZCNT, QWORD, result, asAllocatable(value)));
        } else {
            append(new AMD64Unary.RMOp(TZCNT, DWORD, result, asAllocatable(value)));
        }
        return result;
    }

    @Override
    public Value emitMathAbs(Value input) {
        Variable result = newVariable(LIRKind.combine(input));
        switch ((AMD64Kind) input.getPlatformKind()) {
            case SINGLE:
                append(new AMD64Binary.DataOp(SSEOp.AND, PS, result, asAllocatable(input), JavaConstant.forFloat(Float.intBitsToFloat(0x7FFFFFFF)), 16));
                break;
            case DOUBLE:
                append(new AMD64Binary.DataOp(SSEOp.AND, PD, result, asAllocatable(input), JavaConstant.forDouble(Double.longBitsToDouble(0x7FFFFFFFFFFFFFFFL)), 16));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Value emitMathSqrt(Value input) {
        Variable result = newVariable(LIRKind.combine(input));
        switch ((AMD64Kind) input.getPlatformKind()) {
            case SINGLE:
                append(new AMD64Unary.RMOp(SSEOp.SQRT, SS, result, asAllocatable(input)));
                break;
            case DOUBLE:
                append(new AMD64Unary.RMOp(SSEOp.SQRT, SD, result, asAllocatable(input)));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Value emitMathLog(Value input, boolean base10) {
        Variable result = newVariable(LIRKind.combine(input));
        append(new AMD64MathIntrinsicOp(base10 ? LOG10 : LOG, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathCos(Value input) {
        Variable result = newVariable(LIRKind.combine(input));
        append(new AMD64MathIntrinsicOp(COS, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathSin(Value input) {
        Variable result = newVariable(LIRKind.combine(input));
        append(new AMD64MathIntrinsicOp(SIN, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathTan(Value input) {
        Variable result = newVariable(LIRKind.combine(input));
        append(new AMD64MathIntrinsicOp(TAN, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Variable emitByteSwap(Value input) {
        Variable result = newVariable(LIRKind.combine(input));
        append(new AMD64ByteSwapOp(result, input));
        return result;
    }

    @Override
    public Variable emitArrayEquals(JavaKind kind, Value array1, Value array2, Value length) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        append(new AMD64ArrayEqualsOp(this, kind, result, array1, array2, asAllocatable(length)));
        return result;
    }

    @Override
    public void emitReturn(JavaKind kind, Value input) {
        AllocatableValue operand = Value.ILLEGAL;
        if (input != null) {
            operand = resultOperandFor(kind, input.getLIRKind());
            emitMove(operand, input);
        }
        append(new ReturnOp(operand));
    }

    protected StrategySwitchOp createStrategySwitchOp(SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, Variable key, AllocatableValue temp) {
        return new StrategySwitchOp(strategy, keyTargets, defaultTarget, key, temp);
    }

    @Override
    public void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget) {
        // a temp is needed for loading object constants
        boolean needsTemp = !key.getLIRKind().isValue();
        append(createStrategySwitchOp(strategy, keyTargets, defaultTarget, key, needsTemp ? newVariable(key.getLIRKind()) : Value.ILLEGAL));
    }

    @Override
    protected void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key) {
        append(new TableSwitchOp(lowKey, defaultTarget, targets, key, newVariable(LIRKind.value(target().arch.getWordKind())), newVariable(key.getLIRKind())));
    }

}
