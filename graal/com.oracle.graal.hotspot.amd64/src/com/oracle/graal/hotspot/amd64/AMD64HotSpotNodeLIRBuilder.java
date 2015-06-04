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

import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.*;
import static com.oracle.graal.hotspot.HotSpotBackend.*;
import static com.oracle.jvmci.amd64.AMD64.*;
import static com.oracle.jvmci.code.ValueUtil.*;

import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.AMD64Assembler.*;
import com.oracle.graal.compiler.amd64.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.match.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.nodes.type.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.amd64.AMD64Move.CompareAndSwapOp;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.memory.*;
import com.oracle.jvmci.amd64.*;
import com.oracle.jvmci.code.*;
import com.oracle.jvmci.debug.*;
import com.oracle.jvmci.hotspot.*;
import com.oracle.jvmci.meta.*;

/**
 * LIR generator specialized for AMD64 HotSpot.
 */
public class AMD64HotSpotNodeLIRBuilder extends AMD64NodeLIRBuilder implements HotSpotNodeLIRBuilder {

    private static ValueNode filterCompression(ValueNode node) {
        ValueNode result = node;
        if (result instanceof PiNode) {
            result = ((PiNode) result).getOriginalNode();
        }
        if (result instanceof CompressionNode) {
            result = ((CompressionNode) result).getValue();
        }
        return result;
    }

    private final HotSpotGraalRuntimeProvider runtime;

    private void emitCompareCompressedMemory(Kind kind, IfNode ifNode, ValueNode valueNode, CompressionNode compress, ConstantLocationNode location, Access access, CompareNode compare) {
        Value value = gen.load(operand(valueNode));
        AMD64AddressValue address = makeCompressedAddress(compress, location);
        Condition cond = compare.condition();
        if (access == filterCompression(compare.getX())) {
            cond = cond.mirror();
        } else {
            assert access == filterCompression(compare.getY());
        }

        LabelRef trueLabel = getLIRBlock(ifNode.trueSuccessor());
        LabelRef falseLabel = getLIRBlock(ifNode.falseSuccessor());
        double trueLabelProbability = ifNode.probability(ifNode.trueSuccessor());
        getGen().emitCompareBranchMemory(kind, value, address, getState(access), cond, compare.unorderedIsTrue(), trueLabel, falseLabel, trueLabelProbability);
    }

    public AMD64HotSpotNodeLIRBuilder(HotSpotGraalRuntimeProvider runtime, StructuredGraph graph, LIRGeneratorTool gen) {
        super(graph, gen);
        this.runtime = runtime;
        assert gen instanceof AMD64HotSpotLIRGenerator;
        assert getDebugInfoBuilder() instanceof HotSpotDebugInfoBuilder;
        ((AMD64HotSpotLIRGenerator) gen).setLockStack(((HotSpotDebugInfoBuilder) getDebugInfoBuilder()).lockStack());
    }

    private AMD64HotSpotLIRGenerator getGen() {
        return (AMD64HotSpotLIRGenerator) gen;
    }

    @Override
    protected DebugInfoBuilder createDebugInfoBuilder(StructuredGraph graph, NodeMap<Value> nodeOperands) {
        HotSpotLockStack lockStack = new HotSpotLockStack(gen.getResult().getFrameMapBuilder(), LIRKind.value(Kind.Long));
        return new HotSpotDebugInfoBuilder(nodeOperands, lockStack);
    }

    @Override
    protected void emitPrologue(StructuredGraph graph) {

        CallingConvention incomingArguments = gen.getCallingConvention();

        Value[] params = new Value[incomingArguments.getArgumentCount() + 1];
        for (int i = 0; i < params.length - 1; i++) {
            params[i] = LIRGenerator.toStackKind(incomingArguments.getArgument(i));
            if (isStackSlot(params[i])) {
                StackSlot slot = ValueUtil.asStackSlot(params[i]);
                if (slot.isInCallerFrame() && !gen.getResult().getLIR().hasArgInCallerFrame()) {
                    gen.getResult().getLIR().setHasArgInCallerFrame();
                }
            }
        }
        params[params.length - 1] = rbp.asValue(LIRKind.value(Kind.Long));

        gen.emitIncomingValues(params);

        getGen().emitSaveRbp();

        for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
            Value paramValue = params[param.index()];
            assert paramValue.getLIRKind().equals(getLIRGeneratorTool().getLIRKind(param.stamp()));
            setResult(param, gen.emitMove(paramValue));
        }
    }

    @Override
    public void visitSafepointNode(SafepointNode i) {
        LIRFrameState info = state(i);
        append(new AMD64HotSpotSafepointOp(info, getGen().config, this));
    }

    @Override
    protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        InvokeKind invokeKind = ((HotSpotDirectCallTargetNode) callTarget).invokeKind();
        if (invokeKind.isIndirect()) {
            append(new AMD64HotspotDirectVirtualCallOp(callTarget.targetMethod(), result, parameters, temps, callState, invokeKind, runtime.getConfig()));
        } else {
            assert invokeKind.isDirect();
            HotSpotResolvedJavaMethod resolvedMethod = (HotSpotResolvedJavaMethod) callTarget.targetMethod();
            assert resolvedMethod.isConcrete() : "Cannot make direct call to abstract method.";
            append(new AMD64HotspotDirectStaticCallOp(callTarget.targetMethod(), result, parameters, temps, callState, invokeKind, runtime.getConfig()));
        }
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        if (callTarget instanceof HotSpotIndirectCallTargetNode) {
            Value metaspaceMethodSrc = operand(((HotSpotIndirectCallTargetNode) callTarget).metaspaceMethod());
            Value targetAddressSrc = operand(callTarget.computedAddress());
            AllocatableValue metaspaceMethodDst = AMD64.rbx.asValue(metaspaceMethodSrc.getLIRKind());
            AllocatableValue targetAddressDst = AMD64.rax.asValue(targetAddressSrc.getLIRKind());
            gen.emitMove(metaspaceMethodDst, metaspaceMethodSrc);
            gen.emitMove(targetAddressDst, targetAddressSrc);
            append(new AMD64IndirectCallOp(callTarget.targetMethod(), result, parameters, temps, metaspaceMethodDst, targetAddressDst, callState, runtime.getConfig()));
        } else {
            super.emitIndirectCall(callTarget, result, parameters, temps, callState);
        }
    }

    @Override
    public void emitPatchReturnAddress(ValueNode address) {
        append(new AMD64HotSpotPatchReturnAddressOp(gen.load(operand(address))));
    }

    @Override
    public void emitJumpToExceptionHandlerInCaller(ValueNode handlerInCallerPc, ValueNode exception, ValueNode exceptionPc) {
        Variable handler = gen.load(operand(handlerInCallerPc));
        ForeignCallLinkage linkage = gen.getForeignCalls().lookupForeignCall(EXCEPTION_HANDLER_IN_CALLER);
        CallingConvention outgoingCc = linkage.getOutgoingCallingConvention();
        assert outgoingCc.getArgumentCount() == 2;
        RegisterValue exceptionFixed = (RegisterValue) outgoingCc.getArgument(0);
        RegisterValue exceptionPcFixed = (RegisterValue) outgoingCc.getArgument(1);
        gen.emitMove(exceptionFixed, operand(exception));
        gen.emitMove(exceptionPcFixed, operand(exceptionPc));
        Register thread = getGen().getProviders().getRegisters().getThreadRegister();
        AMD64HotSpotJumpToExceptionHandlerInCallerOp op = new AMD64HotSpotJumpToExceptionHandlerInCallerOp(handler, exceptionFixed, exceptionPcFixed, getGen().config.threadIsMethodHandleReturnOffset,
                        thread, getGen().getSaveRbp().getRbpRescueSlot());
        append(op);
    }

    @Override
    public void visitFullInfopointNode(FullInfopointNode i) {
        if (i.getState() != null && i.getState().bci == BytecodeFrame.AFTER_BCI) {
            Debug.log("Ignoring InfopointNode for AFTER_BCI");
        } else {
            super.visitFullInfopointNode(i);
        }
    }

    public void emitPrefetchAllocate(ValueNode address, ValueNode distance) {
        AMD64AddressValue addr = getGen().emitAddress(operand(address), 0, gen.loadNonConst(operand(distance)), 1);
        append(new AMD64PrefetchOp(addr, getGen().config.allocatePrefetchInstr));
    }

    @Override
    public void visitDirectCompareAndSwap(DirectCompareAndSwapNode x) {
        Value expected = gen.loadNonConst(operand(x.expectedValue()));
        Variable newVal = gen.load(operand(x.newValue()));
        assert expected.getLIRKind().equals(newVal.getLIRKind());

        AMD64AddressValue address = getGen().emitAddress(operand(x.object()), 0, operand(x.offset()), 1);

        RegisterValue raxLocal = AMD64.rax.asValue(expected.getLIRKind());
        gen.emitMove(raxLocal, expected);
        append(new CompareAndSwapOp(expected.getKind(), raxLocal, address, raxLocal, newVal));

        setResult(x, gen.emitMove(raxLocal));
    }

    boolean canFormCompressedMemory(CompressionNode compress, ConstantLocationNode location) {
        HotSpotVMConfig config = runtime.getConfig();
        if (config.useCompressedOops && compress.getEncoding().shift <= 3 && NumUtil.isInt(location.getDisplacement())) {
            Stamp compressedStamp = compress.getValue().stamp();
            if (compressedStamp instanceof NarrowOopStamp) {
                return true;
            } else if (compressedStamp instanceof KlassPointerStamp) {
                assert ((KlassPointerStamp) compressedStamp).isCompressed();
                return config.narrowKlassBase == config.narrowOopBase;
            }
        }
        return false;
    }

    private AMD64AddressValue makeCompressedAddress(CompressionNode compress, ConstantLocationNode location) {
        assert canFormCompressedMemory(compress, location);
        AMD64AddressValue address = getGen().emitAddress(getGen().getProviders().getRegisters().getHeapBaseRegister().asValue(), location.getDisplacement(), operand(compress.getValue()),
                        1 << compress.getEncoding().shift);
        return address;
    }

    @MatchRule("(If (IntegerEquals=compare value (FloatingRead=access (Compression=compress object) ConstantLocation=location)))")
    @MatchRule("(If (IntegerLessThan=compare value (FloatingRead=access (Compression=compress object) ConstantLocation=location)))")
    @MatchRule("(If (IntegerBelow=compare value (FloatingRead=access (Compression=compress object) ConstantLocation=location)))")
    @MatchRule("(If (FloatEquals=compare value (FloatingRead=access (Compression=compress object) ConstantLocation=location)))")
    @MatchRule("(If (FloatLessThan=compare value (FloatingRead=access (Compression=compress object) ConstantLocation=location)))")
    @MatchRule("(If (IntegerEquals=compare value (Read=access (Compression=compress object) ConstantLocation=location)))")
    @MatchRule("(If (IntegerLessThan=compare value (Read=access (Compression=compress object) ConstantLocation=location)))")
    @MatchRule("(If (IntegerBelow=compare value (Read=access (Compression=compress object) ConstantLocation=location)))")
    @MatchRule("(If (FloatEquals=compare value (Read=access (Compression=compress object) ConstantLocation=location)))")
    @MatchRule("(If (FloatLessThan=compare value (Read=access (Compression=compress object) ConstantLocation=location)))")
    public ComplexMatchResult ifCompareCompressedMemory(IfNode root, CompareNode compare, CompressionNode compress, ValueNode value, ConstantLocationNode location, Access access) {
        if (canFormCompressedMemory(compress, location)) {
            PlatformKind cmpKind = gen.getLIRKind(compare.getX().stamp()).getPlatformKind();
            if (cmpKind instanceof Kind) {
                Kind kind = (Kind) cmpKind;
                return builder -> {
                    emitCompareCompressedMemory(kind, root, value, compress, location, access, compare);
                    return null;
                };
            }
        }
        return null;
    }

    private ComplexMatchResult binaryReadCompressed(AMD64RMOp op, OperandSize size, ValueNode value, Access access, CompressionNode compress, ConstantLocationNode location) {
        if (canFormCompressedMemory(compress, location)) {
            return builder -> getLIRGeneratorTool().emitBinaryMemory(op, size, getLIRGeneratorTool().asAllocatable(operand(value)), makeCompressedAddress(compress, location), getState(access));
        } else {
            return null;
        }
    }

    @MatchRule("(Add value (Read=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(Add value (FloatingRead=access (Compression=compress object) ConstantLocation=location))")
    public ComplexMatchResult addMemoryCompressed(ValueNode value, Access access, CompressionNode compress, ConstantLocationNode location) {
        OperandSize size = getMemorySize(access);
        if (size.isXmmType()) {
            return binaryReadCompressed(SSEOp.ADD, size, value, access, compress, location);
        } else {
            return binaryReadCompressed(ADD.getRMOpcode(size), size, value, access, compress, location);
        }
    }

    @MatchRule("(Sub value (Read=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(Sub value (FloatingRead=access (Compression=compress object) ConstantLocation=location))")
    public ComplexMatchResult subMemoryCompressed(ValueNode value, Access access, CompressionNode compress, ConstantLocationNode location) {
        OperandSize size = getMemorySize(access);
        if (size.isXmmType()) {
            return binaryReadCompressed(SSEOp.SUB, size, value, access, compress, location);
        } else {
            return binaryReadCompressed(SUB.getRMOpcode(size), size, value, access, compress, location);
        }
    }

    @MatchRule("(Mul value (Read=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(Mul value (FloatingRead=access (Compression=compress object) ConstantLocation=location))")
    public ComplexMatchResult mulMemoryCompressed(ValueNode value, Access access, CompressionNode compress, ConstantLocationNode location) {
        OperandSize size = getMemorySize(access);
        if (size.isXmmType()) {
            return binaryReadCompressed(SSEOp.MUL, size, value, access, compress, location);
        } else {
            return binaryReadCompressed(AMD64RMOp.IMUL, size, value, access, compress, location);
        }
    }

    @MatchRule("(And value (Read=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(And value (FloatingRead=access (Compression=compress object) ConstantLocation=location))")
    public ComplexMatchResult andMemoryCompressed(ValueNode value, Access access, CompressionNode compress, ConstantLocationNode location) {
        OperandSize size = getMemorySize(access);
        if (size.isXmmType()) {
            return null;
        } else {
            return binaryReadCompressed(AND.getRMOpcode(size), size, value, access, compress, location);
        }
    }

    @MatchRule("(Or value (Read=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(Or value (FloatingRead=access (Compression=compress object) ConstantLocation=location))")
    public ComplexMatchResult orMemoryCompressed(ValueNode value, Access access, CompressionNode compress, ConstantLocationNode location) {
        OperandSize size = getMemorySize(access);
        if (size.isXmmType()) {
            return null;
        } else {
            return binaryReadCompressed(OR.getRMOpcode(size), size, value, access, compress, location);
        }
    }

    @MatchRule("(Xor value (Read=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(Xor value (FloatingRead=access (Compression=compress object) ConstantLocation=location))")
    public ComplexMatchResult xorMemoryCompressed(ValueNode value, Access access, CompressionNode compress, ConstantLocationNode location) {
        OperandSize size = getMemorySize(access);
        if (size.isXmmType()) {
            return null;
        } else {
            return binaryReadCompressed(XOR.getRMOpcode(size), size, value, access, compress, location);
        }
    }

    @MatchRule("(Read (Compression=compress object) ConstantLocation=location)")
    @MatchRule("(Read (Pi (Compression=compress object)) ConstantLocation=location)")
    @MatchRule("(FloatingRead (Compression=compress object) ConstantLocation=location)")
    @MatchRule("(FloatingRead (Pi (Compression=compress object)) ConstantLocation=location)")
    public ComplexMatchResult readCompressed(Access root, CompressionNode compress, ConstantLocationNode location) {
        if (canFormCompressedMemory(compress, location)) {
            LIRKind readKind = getGen().getLIRKind(root.asNode().stamp());
            return builder -> {
                return getGen().emitLoad(readKind, makeCompressedAddress(compress, location), getState(root));
            };
        }
        return null;
    }

    @MatchRule("(Write (Compression=compress object) ConstantLocation=location value)")
    @MatchRule("(Write (Pi (Compression=compress object)) ConstantLocation=location value)")
    public ComplexMatchResult writeCompressed(Access root, CompressionNode compress, ConstantLocationNode location, ValueNode value) {
        if (canFormCompressedMemory(compress, location)) {
            LIRKind readKind = getGen().getLIRKind(value.asNode().stamp());
            return builder -> {
                getGen().emitStore(readKind, makeCompressedAddress(compress, location), operand(value), getState(root));
                return null;
            };
        }
        return null;
    }
}
