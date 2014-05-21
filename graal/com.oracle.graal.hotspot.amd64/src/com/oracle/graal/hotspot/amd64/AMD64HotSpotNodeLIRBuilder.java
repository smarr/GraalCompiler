/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.amd64.AMD64.*;
import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.hotspot.HotSpotBackend.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.AMD64Address.Scale;
import com.oracle.graal.compiler.amd64.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.match.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.amd64.AMD64HotSpotLIRGenerator.SaveRbp;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.nodes.type.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.NoOp;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.amd64.AMD64Move.CompareAndSwapOp;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;

/**
 * LIR generator specialized for AMD64 HotSpot.
 */
public class AMD64HotSpotNodeLIRBuilder extends AMD64NodeLIRBuilder implements HotSpotNodeLIRBuilder {

    private static ValueNode filterCompression(ValueNode node) {
        ValueNode result = node;
        while (result instanceof CompressionNode) {
            result = ((CompressionNode) result).getInput();
        }
        return result;
    }

    private void emitCompareMemoryObject(IfNode ifNode, ValueNode valueNode, Access access, CompareNode compare) {
        Value value;
        // This works by embedding the compressed form for constants, so force a constant instead of
        // respecting what operand() would return.
        if (valueNode.isConstant()) {
            value = valueNode.asConstant();
        } else {
            value = gen.load(operand(valueNode));
        }
        AMD64AddressValue address = makeAddress(access);

        Condition cond = compare.condition();
        Value left;
        Value right;
        if (access == filterCompression(compare.x())) {
            left = value;
            right = address;
        } else {
            assert access == filterCompression(compare.y());
            left = address;
            right = value;
            cond = cond.mirror();
        }

        LabelRef trueLabel = getLIRBlock(ifNode.trueSuccessor());
        LabelRef falseLabel = getLIRBlock(ifNode.falseSuccessor());
        double trueLabelProbability = ifNode.probability(ifNode.trueSuccessor());
        getGen().emitCompareBranchMemoryCompressed(left, right, cond, trueLabel, falseLabel, trueLabelProbability, getState(access));
    }

    private void emitCompareCompressedMemory(Kind kind, IfNode ifNode, ValueNode valueNode, CompressionNode compress, ConstantLocationNode location, Access access, CompareNode compare) {
        Value value = gen.load(operand(valueNode));
        AMD64AddressValue address = makeCompressedAddress(compress, location);
        Condition cond = compare.condition();
        if (access == filterCompression(compare.x())) {
            cond = cond.mirror();
        } else {
            assert access == filterCompression(compare.y());
        }

        LabelRef trueLabel = getLIRBlock(ifNode.trueSuccessor());
        LabelRef falseLabel = getLIRBlock(ifNode.falseSuccessor());
        double trueLabelProbability = ifNode.probability(ifNode.trueSuccessor());
        getGen().emitCompareBranchMemory(kind, value, address, getState(access), cond, compare.unorderedIsTrue(), trueLabel, falseLabel, trueLabelProbability);
    }

    public AMD64HotSpotNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool gen) {
        super(graph, gen);
        assert gen instanceof AMD64HotSpotLIRGenerator;
        assert getDebugInfoBuilder() instanceof HotSpotDebugInfoBuilder;
        ((AMD64HotSpotLIRGenerator) gen).setLockStack(((HotSpotDebugInfoBuilder) getDebugInfoBuilder()).lockStack());
    }

    private AMD64HotSpotLIRGenerator getGen() {
        return (AMD64HotSpotLIRGenerator) gen;
    }

    private SaveRbp getSaveRbp() {
        return getGen().saveRbp;
    }

    private void setSaveRbp(SaveRbp saveRbp) {
        getGen().saveRbp = saveRbp;
    }

    @Override
    protected DebugInfoBuilder createDebugInfoBuilder(StructuredGraph graph, NodeMap<Value> nodeOperands) {
        HotSpotLockStack lockStack = new HotSpotLockStack(gen.getResult().getFrameMap(), Kind.Long);
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
        params[params.length - 1] = rbp.asValue(Kind.Long);

        gen.emitIncomingValues(params);

        setSaveRbp(((AMD64HotSpotLIRGenerator) gen).new SaveRbp(new NoOp(gen.getCurrentBlock(), gen.getResult().getLIR().getLIRforBlock(gen.getCurrentBlock()).size())));
        append(getSaveRbp().placeholder);

        for (ParameterNode param : graph.getNodes(ParameterNode.class)) {
            Value paramValue = params[param.index()];
            assert paramValue.getKind() == param.getKind().getStackKind();
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
        if (invokeKind == InvokeKind.Interface || invokeKind == InvokeKind.Virtual) {
            append(new AMD64HotspotDirectVirtualCallOp(callTarget.target(), result, parameters, temps, callState, invokeKind));
        } else {
            assert invokeKind == InvokeKind.Static || invokeKind == InvokeKind.Special;
            HotSpotResolvedJavaMethod resolvedMethod = (HotSpotResolvedJavaMethod) callTarget.target();
            assert !resolvedMethod.isAbstract() : "Cannot make direct call to abstract method.";
            append(new AMD64HotspotDirectStaticCallOp(callTarget.target(), result, parameters, temps, callState, invokeKind));
        }
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        if (callTarget instanceof HotSpotIndirectCallTargetNode) {
            AllocatableValue metaspaceMethod = AMD64.rbx.asValue();
            gen.emitMove(metaspaceMethod, operand(((HotSpotIndirectCallTargetNode) callTarget).metaspaceMethod()));
            AllocatableValue targetAddress = AMD64.rax.asValue();
            gen.emitMove(targetAddress, operand(callTarget.computedAddress()));
            append(new AMD64IndirectCallOp(callTarget.target(), result, parameters, temps, metaspaceMethod, targetAddress, callState));
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
                        thread);
        append(op);
    }

    @Override
    public void visitInfopointNode(InfopointNode i) {
        if (i.getState() != null && i.getState().bci == BytecodeFrame.AFTER_BCI) {
            Debug.log("Ignoring InfopointNode for AFTER_BCI");
        } else {
            super.visitInfopointNode(i);
        }
    }

    public void emitPrefetchAllocate(ValueNode address, ValueNode distance) {
        AMD64AddressValue addr = getGen().emitAddress(operand(address), 0, gen.loadNonConst(operand(distance)), 1);
        append(new AMD64PrefetchOp(addr, getGen().config.allocatePrefetchInstr));
    }

    @Override
    public void visitDirectCompareAndSwap(DirectCompareAndSwapNode x) {
        Kind kind = x.newValue().getKind();
        assert kind == x.expectedValue().getKind();

        Value expected = gen.loadNonConst(operand(x.expectedValue()));
        Variable newVal = gen.load(operand(x.newValue()));

        int disp = 0;
        AMD64AddressValue address;
        Value index = operand(x.offset());
        if (ValueUtil.isConstant(index) && NumUtil.isInt(ValueUtil.asConstant(index).asLong() + disp)) {
            assert !gen.getCodeCache().needsDataPatch(asConstant(index));
            disp += (int) ValueUtil.asConstant(index).asLong();
            address = new AMD64AddressValue(kind, gen.load(operand(x.object())), disp);
        } else {
            address = new AMD64AddressValue(kind, gen.load(operand(x.object())), gen.load(index), Scale.Times1, disp);
        }

        RegisterValue raxLocal = AMD64.rax.asValue(kind);
        gen.emitMove(raxLocal, expected);
        append(new CompareAndSwapOp(kind, raxLocal, address, raxLocal, newVal));

        Variable result = newVariable(x.getKind());
        gen.emitMove(result, raxLocal);
        setResult(x, result);
    }

    boolean canFormCompressedMemory(CompressionNode compress, ConstantLocationNode location) {
        HotSpotVMConfig config = HotSpotGraalRuntime.runtime().getConfig();
        if (config.useCompressedOops && compress.getEncoding().shift <= 3 && NumUtil.isInt(location.getDisplacement())) {
            PlatformKind objectKind = compress.getInput().stamp().getPlatformKind(getGen());
            if (objectKind == NarrowOopStamp.NarrowOop || objectKind == Kind.Int && config.narrowKlassBase == config.narrowOopBase) {
                return true;
            }
        }
        return false;
    }

    private AMD64AddressValue makeCompressedAddress(CompressionNode compress, ConstantLocationNode location) {
        assert canFormCompressedMemory(compress, location);
        AMD64AddressValue address = getGen().emitAddress(getGen().getProviders().getRegisters().getHeapBaseRegister().asValue(), location.getDisplacement(), operand(compress.getInput()),
                        1 << compress.getEncoding().shift);
        return address;
    }

    @MatchRule("(If (ObjectEquals=compare Constant=value (Pi (Compression Read=access))))")
    @MatchRule("(If (ObjectEquals=compare Constant=value (Pi (Compression FloatingRead=access))))")
    @MatchRule("(If (ObjectEquals=compare (Compression value) (Pi (Compression Read=access))))")
    @MatchRule("(If (ObjectEquals=compare (Compression value) (Pi (Compression FloatingRead=access))))")
    @MatchRule("(If (ObjectEquals=compare Constant=value (Compression Read=access)))")
    @MatchRule("(If (ObjectEquals=compare Constant=value (Compression FloatingRead=access)))")
    @MatchRule("(If (ObjectEquals=compare (Compression value) (Compression Read=access)))")
    @MatchRule("(If (ObjectEquals=compare (Compression value) (Compression FloatingRead=access)))")
    public ComplexMatchResult ifCompareMemoryObject(IfNode root, CompareNode compare, ValueNode value, Access access) {
        if (HotSpotGraalRuntime.runtime().getConfig().useCompressedOops) {
            return builder -> {
                emitCompareMemoryObject(root, value, access, compare);
                return null;
            };
        }
        return null;
    }

    @MatchRule("(If (IntegerEquals=compare value (FloatingRead=access (Compression=compress object) ConstantLocation=location)))")
    @MatchRule("(If (IntegerLessThan=compare value (FloatingRead=access (Compression=compress object) ConstantLocation=location)))")
    @MatchRule("(If (IntegerBelowThan=compare value (FloatingRead=access (Compression=compress object) ConstantLocation=location)))")
    @MatchRule("(If (FloatEquals=compare value (FloatingRead=access (Compression=compress object) ConstantLocation=location)))")
    @MatchRule("(If (FloatLessThan=compare value (FloatingRead=access (Compression=compress object) ConstantLocation=location)))")
    @MatchRule("(If (IntegerEquals=compare value (Read=access (Compression=compress object) ConstantLocation=location)))")
    @MatchRule("(If (IntegerLessThan=compare value (Read=access (Compression=compress object) ConstantLocation=location)))")
    @MatchRule("(If (IntegerBelowThan=compare value (Read=access (Compression=compress object) ConstantLocation=location)))")
    @MatchRule("(If (FloatEquals=compare value (Read=access (Compression=compress object) ConstantLocation=location)))")
    @MatchRule("(If (FloatLessThan=compare value (Read=access (Compression=compress object) ConstantLocation=location)))")
    public ComplexMatchResult ifCompareCompressedMemory(IfNode root, CompareNode compare, CompressionNode compress, ValueNode value, ConstantLocationNode location, Access access) {
        if (canFormCompressedMemory(compress, location)) {
            PlatformKind cmpKind = gen.getPlatformKind(compare.x().stamp());
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

    @MatchRule("(IntegerAdd value (Read=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(IntegerSub value (Read=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(IntegerMul value (Read=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(FloatAdd value (Read=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(FloatSub value (Read=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(FloatMul value (Read=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(Or value (Read=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(Xor value (Read=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(And value (Read=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(IntegerAdd value (FloatingRead=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(IntegerSub value (FloatingRead=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(IntegerMul value (FloatingRead=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(FloatAdd value (FloatingRead=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(FloatSub value (FloatingRead=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(FloatMul value (FloatingRead=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(Or value (FloatingRead=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(Xor value (FloatingRead=access (Compression=compress object) ConstantLocation=location))")
    @MatchRule("(And value (FloatingRead=access (Compression=compress object) ConstantLocation=location))")
    public ComplexMatchResult binaryReadCompressed(BinaryNode root, ValueNode value, Access access, CompressionNode compress, ConstantLocationNode location) {
        if (canFormCompressedMemory(compress, location)) {
            AMD64Arithmetic op = getOp(root, access);
            if (op != null) {
                return builder -> getLIRGeneratorTool().emitBinaryMemory(op, getMemoryKind(access), getLIRGeneratorTool().asAllocatable(operand(value)), makeCompressedAddress(compress, location),
                                getState(access));
            }
        }
        return null;
    }

    @MatchRule("(Read (Compression=compress object) ConstantLocation=location)")
    @MatchRule("(Read (Pi (Compression=compress object)) ConstantLocation=location)")
    @MatchRule("(FloatingRead (Compression=compress object) ConstantLocation=location)")
    @MatchRule("(FloatingRead (Pi (Compression=compress object)) ConstantLocation=location)")
    public ComplexMatchResult readCompressed(Access root, CompressionNode compress, ConstantLocationNode location) {
        if (canFormCompressedMemory(compress, location)) {
            PlatformKind readKind = getGen().getPlatformKind(root.asNode().stamp());
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
            PlatformKind readKind = getGen().getPlatformKind(value.asNode().stamp());
            return builder -> {
                getGen().emitStore(readKind, makeCompressedAddress(compress, location), operand(value), getState(root));
                return null;
            };
        }
        return null;
    }
}
