/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java;

import static com.oracle.graal.api.meta.DeoptimizationAction.*;
import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.bytecode.Bytecodes.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.graph.iterators.NodePredicates.*;
import static com.oracle.graal.nodes.StructuredGraph.*;
import static java.lang.String.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.ProfilingInfo.TriState;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.bytecode.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.*;
import com.oracle.graal.graph.Graph.Mark;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.ValueNumberable;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.java.BciBlockMapping.BciBlock;
import com.oracle.graal.java.BciBlockMapping.ExceptionDispatchBlock;
import com.oracle.graal.java.GraphBuilderPlugin.AnnotatedInvocationPlugin;
import com.oracle.graal.java.GraphBuilderPlugin.InlineInvokePlugin;
import com.oracle.graal.java.GraphBuilderPlugin.InvocationPlugin;
import com.oracle.graal.java.GraphBuilderPlugin.LoopExplosionPlugin;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

/**
 * The {@code GraphBuilder} class parses the bytecode of a method and builds the IR graph.
 */
public class GraphBuilderPhase extends BasePhase<HighTierContext> {

    private final GraphBuilderConfiguration graphBuilderConfig;

    public GraphBuilderPhase(GraphBuilderConfiguration config) {
        this.graphBuilderConfig = config;
    }

    @Override
    protected void run(StructuredGraph graph, HighTierContext context) {
        new Instance(context.getMetaAccess(), context.getStampProvider(), null, context.getConstantReflection(), graphBuilderConfig, context.getOptimisticOptimizations()).run(graph);
    }

    public GraphBuilderConfiguration getGraphBuilderConfig() {
        return graphBuilderConfig;
    }

    public static class Instance extends Phase {

        protected StructuredGraph currentGraph;

        private final MetaAccessProvider metaAccess;

        private ResolvedJavaMethod rootMethod;

        private final GraphBuilderConfiguration graphBuilderConfig;
        private final OptimisticOptimizations optimisticOpts;
        private final StampProvider stampProvider;
        private final ConstantReflectionProvider constantReflection;
        private final SnippetReflectionProvider snippetReflectionProvider;

        /**
         * Gets the graph being processed by this builder.
         */
        protected StructuredGraph getGraph() {
            return currentGraph;
        }

        public Instance(MetaAccessProvider metaAccess, StampProvider stampProvider, SnippetReflectionProvider snippetReflectionProvider, ConstantReflectionProvider constantReflection,
                        GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts) {
            this.graphBuilderConfig = graphBuilderConfig;
            this.optimisticOpts = optimisticOpts;
            this.metaAccess = metaAccess;
            this.stampProvider = stampProvider;
            this.constantReflection = constantReflection;
            this.snippetReflectionProvider = snippetReflectionProvider;
            assert metaAccess != null;
        }

        public Instance(MetaAccessProvider metaAccess, StampProvider stampProvider, ConstantReflectionProvider constantReflection, GraphBuilderConfiguration graphBuilderConfig,
                        OptimisticOptimizations optimisticOpts) {
            this(metaAccess, stampProvider, null, constantReflection, graphBuilderConfig, optimisticOpts);
        }

        @Override
        protected void run(StructuredGraph graph) {
            ResolvedJavaMethod method = graph.method();
            this.rootMethod = method;
            int entryBCI = graph.getEntryBCI();
            assert method.getCode() != null : "method must contain bytecodes: " + method;
            this.currentGraph = graph;
            HIRFrameStateBuilder frameState = new HIRFrameStateBuilder(method, graph, true, null);
            frameState.initializeForMethodStart(graphBuilderConfig.eagerResolving(), this.graphBuilderConfig.getParameterPlugin());
            TTY.Filter filter = new TTY.Filter(PrintFilter.getValue(), method);
            try {
                BytecodeParser parser = new BytecodeParser(metaAccess, method, graphBuilderConfig, optimisticOpts, entryBCI, false);
                parser.build(0, graph.start(), frameState);

                parser.connectLoopEndToBegin();

                // Remove dead parameters.
                for (ParameterNode param : currentGraph.getNodes(ParameterNode.TYPE)) {
                    if (param.hasNoUsages()) {
                        assert param.inputs().isEmpty();
                        param.safeDelete();
                    }
                }

                // Remove redundant begin nodes.
                for (BeginNode beginNode : currentGraph.getNodes(BeginNode.TYPE)) {
                    Node predecessor = beginNode.predecessor();
                    if (predecessor instanceof ControlSplitNode) {
                        // The begin node is necessary.
                    } else {
                        GraphUtil.unlinkFixedNode(beginNode);
                        beginNode.safeDelete();
                    }
                }
            } finally {
                filter.remove();
            }

            ComputeLoopFrequenciesClosure.compute(graph);
        }

        @Override
        protected String getDetailedName() {
            return getName() + " " + rootMethod.format("%H.%n(%p):%r");
        }

        private static class Target {

            FixedNode fixed;
            HIRFrameStateBuilder state;

            public Target(FixedNode fixed, HIRFrameStateBuilder state) {
                this.fixed = fixed;
                this.state = state;
            }
        }

        private static class ExplodedLoopContext {
            private BciBlock header;
            private int targetPeelIteration;
            private int peelIteration;
        }

        public class BytecodeParser extends AbstractBytecodeParser implements GraphBuilderContext {

            private BciBlockMapping blockMap;
            private LocalLiveness liveness;
            protected final int entryBCI;
            private int currentDepth;

            private LineNumberTable lnt;
            private int previousLineNumber;
            private int currentLineNumber;

            private ValueNode methodSynchronizedObject;

            private ValueNode returnValue;
            private FixedWithNextNode beforeReturnNode;
            private ValueNode unwindValue;
            private FixedWithNextNode beforeUnwindNode;

            private FixedWithNextNode lastInstr;                 // the last instruction added
            private final boolean explodeLoops;
            private Stack<ExplodedLoopContext> explodeLoopsContext;
            private int nextPeelIteration = 1;
            private boolean controlFlowSplit;

            private FixedWithNextNode[] firstInstructionArray;
            private HIRFrameStateBuilder[] entryStateArray;
            private FixedWithNextNode[][] firstInstructionMatrix;
            private HIRFrameStateBuilder[][] entryStateMatrix;

            /**
             * @param isReplacement specifies if this object is being used to parse a method that
             *            implements the semantics of another method (i.e., an intrinsic) or
             *            bytecode instruction (i.e., a snippet)
             */
            public BytecodeParser(MetaAccessProvider metaAccess, ResolvedJavaMethod method, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, int entryBCI,
                            boolean isReplacement) {
                super(metaAccess, method, graphBuilderConfig, optimisticOpts, isReplacement);
                this.entryBCI = entryBCI;

                if (graphBuilderConfig.insertNonSafepointDebugInfo()) {
                    lnt = method.getLineNumberTable();
                    previousLineNumber = -1;
                }

                LoopExplosionPlugin loopExplosionPlugin = graphBuilderConfig.getLoopExplosionPlugin();
                if (loopExplosionPlugin != null) {
                    explodeLoops = loopExplosionPlugin.shouldExplodeLoops(method);
                } else {
                    explodeLoops = false;
                }
            }

            public ValueNode getReturnValue() {
                return returnValue;
            }

            public FixedWithNextNode getBeforeReturnNode() {
                return this.beforeReturnNode;
            }

            public ValueNode getUnwindValue() {
                return unwindValue;
            }

            public FixedWithNextNode getBeforeUnwindNode() {
                return this.beforeUnwindNode;
            }

            protected void build(int depth, FixedWithNextNode startInstruction, HIRFrameStateBuilder startFrameState) {
                this.currentDepth = depth;
                if (PrintProfilingInformation.getValue() && profilingInfo != null) {
                    TTY.println("Profiling info for " + method.format("%H.%n(%p)"));
                    TTY.println(MetaUtil.indent(profilingInfo.toString(method, CodeUtil.NEW_LINE), "  "));
                }

                try (Indent indent = Debug.logAndIndent("build graph for %s", method)) {

                    // compute the block map, setup exception handlers and get the entrypoint(s)
                    BciBlockMapping newMapping = BciBlockMapping.create(stream, method);
                    this.blockMap = newMapping;
                    this.firstInstructionArray = new FixedWithNextNode[blockMap.getBlockCount()];
                    this.entryStateArray = new HIRFrameStateBuilder[blockMap.getBlockCount()];

                    if (graphBuilderConfig.doLivenessAnalysis()) {
                        try (Scope s = Debug.scope("LivenessAnalysis")) {
                            int maxLocals = method.getMaxLocals();
                            liveness = LocalLiveness.compute(stream, blockMap.getBlocks(), maxLocals, blockMap.getLoopCount());
                        } catch (Throwable e) {
                            throw Debug.handle(e);
                        }
                    }

                    lastInstr = startInstruction;
                    this.setCurrentFrameState(startFrameState);
                    stream.setBCI(0);

                    BciBlock startBlock = blockMap.getStartBlock();
                    if (startInstruction == currentGraph.start()) {
                        StartNode startNode = currentGraph.start();
                        if (method.isSynchronized()) {
                            startNode.setStateAfter(frameState.create(BytecodeFrame.BEFORE_BCI));
                        } else {
                            frameState.clearNonLiveLocals(startBlock, liveness, true);
                            assert bci() == 0;
                            startNode.setStateAfter(frameState.create(bci()));
                        }
                    }

                    if (method.isSynchronized()) {
                        // add a monitor enter to the start block
                        methodSynchronizedObject = synchronizedObject(frameState, method);
                        MonitorEnterNode monitorEnter = genMonitorEnter(methodSynchronizedObject);
                        frameState.clearNonLiveLocals(startBlock, liveness, true);
                        assert bci() == 0;
                        monitorEnter.setStateAfter(frameState.create(bci()));
                    }

                    if (graphBuilderConfig.insertNonSafepointDebugInfo()) {
                        append(createInfoPointNode(InfopointReason.METHOD_START));
                    }

                    currentBlock = blockMap.getStartBlock();
                    setEntryState(startBlock, 0, frameState);
                    if (startBlock.isLoopHeader && !explodeLoops) {
                        appendGoto(startBlock);
                    } else {
                        setFirstInstruction(startBlock, 0, lastInstr);
                    }

                    int index = 0;
                    BciBlock[] blocks = blockMap.getBlocks();
                    while (index < blocks.length) {
                        BciBlock block = blocks[index];
                        index = iterateBlock(blocks, block);
                    }

                    if (Debug.isDumpEnabled() && DumpDuringGraphBuilding.getValue() && this.beforeReturnNode != startInstruction) {
                        Debug.dump(currentGraph, "Bytecodes parsed: " + method.getDeclaringClass().getUnqualifiedName() + "." + method.getName());
                    }
                }
            }

            private int iterateBlock(BciBlock[] blocks, BciBlock block) {
                if (block.isLoopHeader && this.explodeLoops) {
                    return iterateExplodedLoopHeader(blocks, block);
                } else {
                    processBlock(this, block);
                    return block.getId() + 1;
                }
            }

            private int iterateExplodedLoopHeader(BciBlock[] blocks, BciBlock header) {
                if (explodeLoopsContext == null) {
                    explodeLoopsContext = new Stack<>();
                }

                ExplodedLoopContext context = new ExplodedLoopContext();
                context.header = header;
                context.peelIteration = this.getCurrentDimension();
                context.targetPeelIteration = -1;
                explodeLoopsContext.push(context);
                if (Debug.isDumpEnabled() && DumpDuringGraphBuilding.getValue()) {
                    Debug.dump(currentGraph, "before loop explosion dimension " + context.peelIteration);
                }

                while (true) {

                    processBlock(this, header);
                    for (int j = header.getId() + 1; j <= header.loopEnd; ++j) {
                        BciBlock block = blocks[j];
                        iterateBlock(blocks, block);
                    }

                    if (context.targetPeelIteration != -1) {
                        // We were reaching the backedge during explosion. Explode further.
                        context.peelIteration = context.targetPeelIteration;
                        context.targetPeelIteration = -1;
                        if (Debug.isDumpEnabled() && DumpDuringGraphBuilding.getValue()) {
                            Debug.dump(currentGraph, "next loop explosion iteration " + context.peelIteration);
                        }
                    } else {
                        // We did not reach the backedge. Exit.
                        break;
                    }
                }
                explodeLoopsContext.pop();
                return header.loopEnd + 1;
            }

            /**
             * @param type the unresolved type of the constant
             */
            @Override
            protected void handleUnresolvedLoadConstant(JavaType type) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
            }

            /**
             * @param type the unresolved type of the type check
             * @param object the object value whose type is being checked against {@code type}
             */
            @Override
            protected void handleUnresolvedCheckCast(JavaType type, ValueNode object) {
                assert !graphBuilderConfig.eagerResolving();
                append(new FixedGuardNode(currentGraph.unique(new IsNullNode(object)), Unresolved, InvalidateRecompile));
                frameState.apush(appendConstant(JavaConstant.NULL_POINTER));
            }

            /**
             * @param type the unresolved type of the type check
             * @param object the object value whose type is being checked against {@code type}
             */
            @Override
            protected void handleUnresolvedInstanceOf(JavaType type, ValueNode object) {
                assert !graphBuilderConfig.eagerResolving();
                AbstractBeginNode successor = currentGraph.add(new BeginNode());
                DeoptimizeNode deopt = currentGraph.add(new DeoptimizeNode(InvalidateRecompile, Unresolved));
                append(new IfNode(currentGraph.unique(new IsNullNode(object)), successor, deopt, 1));
                lastInstr = successor;
                frameState.ipush(appendConstant(JavaConstant.INT_0));
            }

            /**
             * @param type the type being instantiated
             */
            @Override
            protected void handleUnresolvedNewInstance(JavaType type) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
            }

            /**
             * @param type the type of the array being instantiated
             * @param length the length of the array
             */
            @Override
            protected void handleUnresolvedNewObjectArray(JavaType type, ValueNode length) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
            }

            /**
             * @param type the type being instantiated
             * @param dims the dimensions for the multi-array
             */
            @Override
            protected void handleUnresolvedNewMultiArray(JavaType type, List<ValueNode> dims) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
            }

            /**
             * @param field the unresolved field
             * @param receiver the object containing the field or {@code null} if {@code field} is
             *            static
             */
            @Override
            protected void handleUnresolvedLoadField(JavaField field, ValueNode receiver) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
            }

            /**
             * @param field the unresolved field
             * @param value the value being stored to the field
             * @param receiver the object containing the field or {@code null} if {@code field} is
             *            static
             */
            @Override
            protected void handleUnresolvedStoreField(JavaField field, ValueNode value, ValueNode receiver) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
            }

            /**
             * @param type
             */
            @Override
            protected void handleUnresolvedExceptionType(JavaType type) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
            }

            /**
             * @param javaMethod
             * @param invokeKind
             */
            protected void handleUnresolvedInvoke(JavaMethod javaMethod, InvokeKind invokeKind) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
            }

            private DispatchBeginNode handleException(ValueNode exceptionObject, int bci) {
                assert bci == BytecodeFrame.BEFORE_BCI || bci == bci() : "invalid bci";
                Debug.log("Creating exception dispatch edges at %d, exception object=%s, exception seen=%s", bci, exceptionObject, (profilingInfo == null ? "" : profilingInfo.getExceptionSeen(bci)));

                BciBlock dispatchBlock = currentBlock.exceptionDispatchBlock();
                /*
                 * The exception dispatch block is always for the last bytecode of a block, so if we
                 * are not at the endBci yet, there is no exception handler for this bci and we can
                 * unwind immediately.
                 */
                if (bci != currentBlock.endBci || dispatchBlock == null) {
                    dispatchBlock = blockMap.getUnwindBlock();
                }

                HIRFrameStateBuilder dispatchState = frameState.copy();
                dispatchState.clearStack();

                DispatchBeginNode dispatchBegin;
                if (exceptionObject == null) {
                    dispatchBegin = currentGraph.add(new ExceptionObjectNode(metaAccess));
                    dispatchState.apush(dispatchBegin);
                    dispatchState.setRethrowException(true);
                    dispatchBegin.setStateAfter(dispatchState.create(bci));
                } else {
                    dispatchBegin = currentGraph.add(new DispatchBeginNode());
                    dispatchState.apush(exceptionObject);
                    dispatchBegin.setStateAfter(dispatchState.create(bci));
                    dispatchState.setRethrowException(true);
                }
                this.controlFlowSplit = true;
                FixedNode target = createTarget(dispatchBlock, dispatchState);
                FixedWithNextNode finishedDispatch = finishInstruction(dispatchBegin, dispatchState);
                finishedDispatch.setNext(target);
                return dispatchBegin;
            }

            @Override
            protected ValueNode genLoadIndexed(ValueNode array, ValueNode index, Kind kind) {
                return LoadIndexedNode.create(array, index, kind, metaAccess, constantReflection);
            }

            @Override
            protected ValueNode genStoreIndexed(ValueNode array, ValueNode index, Kind kind, ValueNode value) {
                return new StoreIndexedNode(array, index, kind, value);
            }

            @Override
            protected ValueNode genIntegerAdd(Kind kind, ValueNode x, ValueNode y) {
                return AddNode.create(x, y);
            }

            @Override
            protected ValueNode genIntegerSub(Kind kind, ValueNode x, ValueNode y) {
                return SubNode.create(x, y);
            }

            @Override
            protected ValueNode genIntegerMul(Kind kind, ValueNode x, ValueNode y) {
                return MulNode.create(x, y);
            }

            @Override
            protected ValueNode genFloatAdd(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP) {
                return AddNode.create(x, y);
            }

            @Override
            protected ValueNode genFloatSub(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP) {
                return SubNode.create(x, y);
            }

            @Override
            protected ValueNode genFloatMul(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP) {
                return MulNode.create(x, y);
            }

            @Override
            protected ValueNode genFloatDiv(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP) {
                return DivNode.create(x, y);
            }

            @Override
            protected ValueNode genFloatRem(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP) {
                return new RemNode(x, y);
            }

            @Override
            protected ValueNode genIntegerDiv(Kind kind, ValueNode x, ValueNode y) {
                return new IntegerDivNode(x, y);
            }

            @Override
            protected ValueNode genIntegerRem(Kind kind, ValueNode x, ValueNode y) {
                return new IntegerRemNode(x, y);
            }

            @Override
            protected ValueNode genNegateOp(ValueNode x) {
                return (new NegateNode(x));
            }

            @Override
            protected ValueNode genLeftShift(Kind kind, ValueNode x, ValueNode y) {
                return new LeftShiftNode(x, y);
            }

            @Override
            protected ValueNode genRightShift(Kind kind, ValueNode x, ValueNode y) {
                return new RightShiftNode(x, y);
            }

            @Override
            protected ValueNode genUnsignedRightShift(Kind kind, ValueNode x, ValueNode y) {
                return new UnsignedRightShiftNode(x, y);
            }

            @Override
            protected ValueNode genAnd(Kind kind, ValueNode x, ValueNode y) {
                return AndNode.create(x, y);
            }

            @Override
            protected ValueNode genOr(Kind kind, ValueNode x, ValueNode y) {
                return OrNode.create(x, y);
            }

            @Override
            protected ValueNode genXor(Kind kind, ValueNode x, ValueNode y) {
                return XorNode.create(x, y);
            }

            @Override
            protected ValueNode genNormalizeCompare(ValueNode x, ValueNode y, boolean isUnorderedLess) {
                return NormalizeCompareNode.create(x, y, isUnorderedLess, constantReflection);
            }

            @Override
            protected ValueNode genFloatConvert(FloatConvert op, ValueNode input) {
                return FloatConvertNode.create(op, input);
            }

            @Override
            protected ValueNode genNarrow(ValueNode input, int bitCount) {
                return NarrowNode.create(input, bitCount);
            }

            @Override
            protected ValueNode genSignExtend(ValueNode input, int bitCount) {
                return SignExtendNode.create(input, bitCount);
            }

            @Override
            protected ValueNode genZeroExtend(ValueNode input, int bitCount) {
                return ZeroExtendNode.create(input, bitCount);
            }

            @Override
            protected void genGoto() {
                appendGoto(currentBlock.getSuccessor(0));
                assert currentBlock.numNormalSuccessors() == 1;
            }

            @Override
            protected LogicNode genObjectEquals(ValueNode x, ValueNode y) {
                return ObjectEqualsNode.create(x, y, constantReflection);
            }

            @Override
            protected LogicNode genIntegerEquals(ValueNode x, ValueNode y) {
                return IntegerEqualsNode.create(x, y, constantReflection);
            }

            @Override
            protected LogicNode genIntegerLessThan(ValueNode x, ValueNode y) {
                return IntegerLessThanNode.create(x, y, constantReflection);
            }

            @Override
            protected ValueNode genUnique(ValueNode x) {
                return (ValueNode) currentGraph.unique((Node & ValueNumberable) x);
            }

            protected ValueNode genIfNode(LogicNode condition, FixedNode falseSuccessor, FixedNode trueSuccessor, double d) {
                return new IfNode(condition, falseSuccessor, trueSuccessor, d);
            }

            @Override
            protected void genThrow() {
                ValueNode exception = frameState.apop();
                append(new FixedGuardNode(currentGraph.unique(new IsNullNode(exception)), NullCheckException, InvalidateReprofile, true));
                lastInstr.setNext(handleException(exception, bci()));
            }

            @Override
            protected ValueNode createCheckCast(ResolvedJavaType type, ValueNode object, JavaTypeProfile profileForTypeCheck, boolean forStoreCheck) {
                return CheckCastNode.create(type, object, profileForTypeCheck, forStoreCheck, currentGraph.getAssumptions());
            }

            @Override
            protected ValueNode createInstanceOf(ResolvedJavaType type, ValueNode object, JavaTypeProfile profileForTypeCheck) {
                return InstanceOfNode.create(type, object, profileForTypeCheck);
            }

            @Override
            protected ValueNode genConditional(ValueNode x) {
                return new ConditionalNode((LogicNode) x);
            }

            @Override
            protected NewInstanceNode createNewInstance(ResolvedJavaType type, boolean fillContents) {
                return new NewInstanceNode(type, fillContents);
            }

            @Override
            protected NewArrayNode createNewArray(ResolvedJavaType elementType, ValueNode length, boolean fillContents) {
                return new NewArrayNode(elementType, length, fillContents);
            }

            @Override
            protected NewMultiArrayNode createNewMultiArray(ResolvedJavaType type, List<ValueNode> dimensions) {
                return new NewMultiArrayNode(type, dimensions.toArray(new ValueNode[0]));
            }

            @Override
            protected ValueNode genLoadField(ValueNode receiver, ResolvedJavaField field) {
                return new LoadFieldNode(receiver, field);
            }

            @Override
            protected void emitNullCheck(ValueNode receiver) {
                if (StampTool.isPointerNonNull(receiver.stamp())) {
                    return;
                }
                BytecodeExceptionNode exception = currentGraph.add(new BytecodeExceptionNode(metaAccess, NullPointerException.class));
                AbstractBeginNode falseSucc = currentGraph.add(new BeginNode());
                append(new IfNode(currentGraph.unique(new IsNullNode(receiver)), exception, falseSucc, 0.01));
                lastInstr = falseSucc;

                exception.setStateAfter(frameState.create(bci()));
                exception.setNext(handleException(exception, bci()));
            }

            @Override
            protected void emitBoundsCheck(ValueNode index, ValueNode length) {
                AbstractBeginNode trueSucc = currentGraph.add(new BeginNode());
                BytecodeExceptionNode exception = currentGraph.add(new BytecodeExceptionNode(metaAccess, ArrayIndexOutOfBoundsException.class, index));
                append(new IfNode(currentGraph.unique(IntegerBelowNode.create(index, length, constantReflection)), trueSucc, exception, 0.99));
                lastInstr = trueSucc;

                exception.setStateAfter(frameState.create(bci()));
                exception.setNext(handleException(exception, bci()));
            }

            @Override
            protected ValueNode genArrayLength(ValueNode x) {
                return ArrayLengthNode.create(x, constantReflection);
            }

            @Override
            protected ValueNode genStoreField(ValueNode receiver, ResolvedJavaField field, ValueNode value) {
                return new StoreFieldNode(receiver, field, value);
            }

            /**
             * Ensure that concrete classes are at least linked before generating an invoke.
             * Interfaces may never be linked so simply return true for them.
             *
             * @param target
             * @return true if the declared holder is an interface or is linked
             */
            private boolean callTargetIsResolved(JavaMethod target) {
                if (target instanceof ResolvedJavaMethod) {
                    ResolvedJavaMethod resolvedTarget = (ResolvedJavaMethod) target;
                    ResolvedJavaType resolvedType = resolvedTarget.getDeclaringClass();
                    return resolvedType.isInterface() || resolvedType.isLinked();
                }
                return false;
            }

            @Override
            protected void genInvokeStatic(JavaMethod target) {
                if (callTargetIsResolved(target)) {
                    ResolvedJavaMethod resolvedTarget = (ResolvedJavaMethod) target;
                    ResolvedJavaType holder = resolvedTarget.getDeclaringClass();
                    if (!holder.isInitialized() && ResolveClassBeforeStaticInvoke.getValue()) {
                        handleUnresolvedInvoke(target, InvokeKind.Static);
                    } else {
                        ValueNode[] args = frameState.popArguments(resolvedTarget.getSignature().getParameterCount(false));
                        appendInvoke(InvokeKind.Static, resolvedTarget, args);
                    }
                } else {
                    handleUnresolvedInvoke(target, InvokeKind.Static);
                }
            }

            @Override
            protected void genInvokeInterface(JavaMethod target) {
                if (callTargetIsResolved(target)) {
                    ValueNode[] args = frameState.popArguments(target.getSignature().getParameterCount(true));
                    appendInvoke(InvokeKind.Interface, (ResolvedJavaMethod) target, args);
                } else {
                    handleUnresolvedInvoke(target, InvokeKind.Interface);
                }
            }

            @Override
            protected void genInvokeDynamic(JavaMethod target) {
                if (target instanceof ResolvedJavaMethod) {
                    JavaConstant appendix = constantPool.lookupAppendix(stream.readCPI4(), Bytecodes.INVOKEDYNAMIC);
                    if (appendix != null) {
                        frameState.apush(ConstantNode.forConstant(appendix, metaAccess, currentGraph));
                    }
                    ValueNode[] args = frameState.popArguments(target.getSignature().getParameterCount(false));
                    appendInvoke(InvokeKind.Static, (ResolvedJavaMethod) target, args);
                } else {
                    handleUnresolvedInvoke(target, InvokeKind.Static);
                }
            }

            @Override
            protected void genInvokeVirtual(JavaMethod target) {
                if (callTargetIsResolved(target)) {
                    /*
                     * Special handling for runtimes that rewrite an invocation of
                     * MethodHandle.invoke(...) or MethodHandle.invokeExact(...) to a static
                     * adapter. HotSpot does this - see
                     * https://wikis.oracle.com/display/HotSpotInternals/Method+handles
                     * +and+invokedynamic
                     */
                    boolean hasReceiver = !((ResolvedJavaMethod) target).isStatic();
                    JavaConstant appendix = constantPool.lookupAppendix(stream.readCPI(), Bytecodes.INVOKEVIRTUAL);
                    if (appendix != null) {
                        frameState.apush(ConstantNode.forConstant(appendix, metaAccess, currentGraph));
                    }
                    ValueNode[] args = frameState.popArguments(target.getSignature().getParameterCount(hasReceiver));
                    if (hasReceiver) {
                        appendInvoke(InvokeKind.Virtual, (ResolvedJavaMethod) target, args);
                    } else {
                        appendInvoke(InvokeKind.Static, (ResolvedJavaMethod) target, args);
                    }
                } else {
                    handleUnresolvedInvoke(target, InvokeKind.Virtual);
                }

            }

            @Override
            protected void genInvokeSpecial(JavaMethod target) {
                if (callTargetIsResolved(target)) {
                    assert target != null;
                    assert target.getSignature() != null;
                    ValueNode[] args = frameState.popArguments(target.getSignature().getParameterCount(true));
                    appendInvoke(InvokeKind.Special, (ResolvedJavaMethod) target, args);
                } else {
                    handleUnresolvedInvoke(target, InvokeKind.Special);
                }
            }

            private void appendInvoke(InvokeKind initialInvokeKind, ResolvedJavaMethod initialTargetMethod, ValueNode[] args) {
                ResolvedJavaMethod targetMethod = initialTargetMethod;
                InvokeKind invokeKind = initialInvokeKind;
                if (initialInvokeKind.isIndirect()) {
                    ResolvedJavaType contextType = this.frameState.method.getDeclaringClass();
                    ResolvedJavaMethod specialCallTarget = MethodCallTargetNode.findSpecialCallTarget(initialInvokeKind, args[0], initialTargetMethod, contextType);
                    if (specialCallTarget != null) {
                        invokeKind = InvokeKind.Special;
                        targetMethod = specialCallTarget;
                    }
                }

                Kind resultType = targetMethod.getSignature().getReturnKind();
                if (DeoptALot.getValue()) {
                    append(new DeoptimizeNode(DeoptimizationAction.None, RuntimeConstraint));
                    frameState.pushReturn(resultType, ConstantNode.defaultForKind(resultType, currentGraph));
                    return;
                }

                JavaType returnType = targetMethod.getSignature().getReturnType(method.getDeclaringClass());
                if (graphBuilderConfig.eagerResolving()) {
                    returnType = returnType.resolve(targetMethod.getDeclaringClass());
                }
                if (invokeKind.hasReceiver()) {
                    emitExplicitExceptions(args[0], null);
                    if (invokeKind.isIndirect() && profilingInfo != null && this.optimisticOpts.useTypeCheckHints()) {
                        JavaTypeProfile profile = profilingInfo.getTypeProfile(bci());
                        args[0] = TypeProfileProxyNode.proxify(args[0], profile);
                    }
                }

                if (tryInvocationPlugin(args, targetMethod, resultType)) {
                    if (GraalOptions.TraceInlineDuringParsing.getValue()) {
                        TTY.println(format("%sUsed invocation plugin for %s", nSpaces(currentDepth), targetMethod));
                    }
                    return;
                }

                if (tryAnnotatedInvocationPlugin(args, targetMethod)) {
                    if (GraalOptions.TraceInlineDuringParsing.getValue()) {
                        TTY.println(format("%sUsed annotated invocation plugin for %s", nSpaces(currentDepth), targetMethod));
                    }
                    return;
                }

                if (tryInline(args, targetMethod, invokeKind, returnType)) {
                    return;
                }

                MethodCallTargetNode callTarget = currentGraph.add(createMethodCallTarget(invokeKind, targetMethod, args, returnType));

                // be conservative if information was not recorded (could result in endless
                // recompiles otherwise)
                if (graphBuilderConfig.omitAllExceptionEdges() || (optimisticOpts.useExceptionProbability() && profilingInfo != null && profilingInfo.getExceptionSeen(bci()) == TriState.FALSE)) {
                    createInvoke(callTarget, resultType);
                } else {
                    InvokeWithExceptionNode invoke = createInvokeWithException(callTarget, resultType);
                    AbstractBeginNode beginNode = currentGraph.add(new KillingBeginNode(LocationIdentity.ANY_LOCATION));
                    invoke.setNext(beginNode);
                    lastInstr = beginNode;
                }
            }

            private boolean tryInvocationPlugin(ValueNode[] args, ResolvedJavaMethod targetMethod, Kind resultType) {
                InvocationPlugin plugin = graphBuilderConfig.getInvocationPlugins().lookupInvocation(targetMethod);
                if (plugin != null) {
                    int beforeStackSize = frameState.stackSize;
                    boolean needsNullCheck = !targetMethod.isStatic() && args[0].getKind() == Kind.Object && !StampTool.isPointerNonNull(args[0].stamp());
                    int nodeCount = currentGraph.getNodeCount();
                    Mark mark = needsNullCheck ? currentGraph.getMark() : null;
                    if (InvocationPlugin.execute(this, plugin, args)) {
                        assert beforeStackSize + resultType.getSlotCount() == frameState.stackSize : "plugin manipulated the stack incorrectly " + targetMethod;
                        assert !needsNullCheck || args[0].usages().filter(isNotA(FrameState.class)).isEmpty() || containsNullCheckOf(currentGraph.getNewNodes(mark), args[0]) : "plugin needs to null check the receiver of " +
                                        targetMethod + ": " + args[0];
                        return true;
                    }
                    assert nodeCount == currentGraph.getNodeCount() : "plugin that returns false must not create new nodes";
                    assert beforeStackSize == frameState.stackSize : "plugin that returns false must modify the stack";
                }
                return false;
            }

            private boolean tryAnnotatedInvocationPlugin(ValueNode[] args, ResolvedJavaMethod targetMethod) {
                AnnotatedInvocationPlugin plugin = graphBuilderConfig.getAnnotatedInvocationPlugin();
                return plugin != null && plugin.apply(this, targetMethod, args);
            }

            private boolean containsNullCheckOf(NodeIterable<Node> nodes, Node value) {
                for (Node n : nodes) {
                    if (n instanceof GuardingPiNode) {
                        GuardingPiNode pi = (GuardingPiNode) n;
                        if (pi.condition() instanceof IsNullNode) {
                            return ((IsNullNode) pi.condition()).getValue() == value;
                        }
                    }
                }
                return false;
            }

            private boolean tryInline(ValueNode[] args, ResolvedJavaMethod targetMethod, InvokeKind invokeKind, JavaType returnType) {
                InlineInvokePlugin plugin = graphBuilderConfig.getInlineInvokePlugin();
                if (plugin == null || !invokeKind.isDirect() || !targetMethod.canBeInlined()) {
                    return false;
                }
                ResolvedJavaMethod inlinedMethod = plugin.getInlinedMethod(this, targetMethod, args, returnType, currentDepth);
                if (inlinedMethod != null && inlinedMethod.hasBytecodes()) {
                    if (TraceInlineDuringParsing.getValue()) {
                        int bci = this.bci();
                        StackTraceElement ste = this.method.asStackTraceElement(bci);
                        TTY.println(format("%s%s (%s:%d) inlining call to %s", nSpaces(currentDepth), method.getName(), ste.getFileName(), ste.getLineNumber(), inlinedMethod.format("%h.%n(%p)")));
                    }
                    parseAndInlineCallee(inlinedMethod, args, parsingReplacement || !inlinedMethod.equals(targetMethod));
                    plugin.postInline(inlinedMethod);
                    return true;
                }

                return false;
            }

            private void parseAndInlineCallee(ResolvedJavaMethod targetMethod, ValueNode[] args, boolean isReplacement) {
                BytecodeParser parser = new BytecodeParser(metaAccess, targetMethod, graphBuilderConfig, optimisticOpts, INVOCATION_ENTRY_BCI, isReplacement);
                final FrameState[] lazyFrameState = new FrameState[1];

                // Replacements often produce nodes with an illegal kind (e.g., pointer stamps)
                // so the frame state builder should not check the types flowing through the frame
                // since all such assertions are in terms of Java kinds.
                boolean checkTypes = !isReplacement;

                HIRFrameStateBuilder startFrameState = new HIRFrameStateBuilder(targetMethod, currentGraph, checkTypes, () -> {
                    if (lazyFrameState[0] == null) {
                        lazyFrameState[0] = frameState.create(bci());
                    }
                    return lazyFrameState[0];
                });
                startFrameState.initializeFromArgumentsArray(args);
                parser.build(currentDepth + 1, this.lastInstr, startFrameState);

                FixedWithNextNode calleeBeforeReturnNode = parser.getBeforeReturnNode();
                this.lastInstr = calleeBeforeReturnNode;
                if (calleeBeforeReturnNode != null) {
                    ValueNode calleeReturnValue = parser.getReturnValue();
                    if (calleeReturnValue != null) {
                        frameState.push(calleeReturnValue.getKind().getStackKind(), calleeReturnValue);
                    }
                }

                FixedWithNextNode calleeBeforeUnwindNode = parser.getBeforeUnwindNode();
                if (calleeBeforeUnwindNode != null) {
                    ValueNode calleeUnwindValue = parser.getUnwindValue();
                    assert calleeUnwindValue != null;
                    if (calleeBeforeUnwindNode instanceof AbstractMergeNode) {
                        AbstractMergeNode mergeNode = (AbstractMergeNode) calleeBeforeUnwindNode;
                        HIRFrameStateBuilder dispatchState = frameState.copy();
                        dispatchState.clearStack();
                        dispatchState.apush(calleeUnwindValue);
                        dispatchState.setRethrowException(true);
                        mergeNode.setStateAfter(dispatchState.create(bci()));

                    }
                    calleeBeforeUnwindNode.setNext(handleException(calleeUnwindValue, bci()));
                }

                // Record inlined method dependency in the graph
                if (currentGraph.isInlinedMethodRecordingEnabled()) {
                    currentGraph.getInlinedMethods().add(targetMethod);
                }
            }

            protected MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, JavaType returnType) {
                return new MethodCallTargetNode(invokeKind, targetMethod, args, returnType);
            }

            protected InvokeNode createInvoke(CallTargetNode callTarget, Kind resultType) {
                InvokeNode invoke = append(new InvokeNode(callTarget, bci()));
                frameState.pushReturn(resultType, invoke);
                return invoke;
            }

            protected InvokeWithExceptionNode createInvokeWithException(CallTargetNode callTarget, Kind resultType) {
                DispatchBeginNode exceptionEdge = handleException(null, bci());
                InvokeWithExceptionNode invoke = append(new InvokeWithExceptionNode(callTarget, exceptionEdge, bci()));
                frameState.pushReturn(resultType, invoke);
                invoke.setStateAfter(frameState.create(stream.nextBCI()));
                return invoke;
            }

            @Override
            protected void genReturn(ValueNode x) {

                if (this.currentDepth == 0) {
                    frameState.setRethrowException(false);
                    frameState.clearStack();
                    beforeReturn(x);
                    append(new ReturnNode(x));
                } else {
                    if (blockMap.getReturnCount() == 1 || !controlFlowSplit) {
                        // There is only a single return.
                        beforeReturn(x);
                        this.returnValue = x;
                        this.beforeReturnNode = this.lastInstr;
                        this.lastInstr = null;
                    } else {
                        frameState.setRethrowException(false);
                        frameState.clearStack();
                        if (x != null) {
                            frameState.push(x.getKind(), x);
                        }
                        assert blockMap.getReturnCount() > 1;
                        appendGoto(blockMap.getReturnBlock());
                    }
                }
            }

            private void beforeReturn(ValueNode x) {
                if (graphBuilderConfig.insertNonSafepointDebugInfo()) {
                    append(createInfoPointNode(InfopointReason.METHOD_END));
                }

                synchronizedEpilogue(BytecodeFrame.AFTER_BCI, x);
                if (frameState.lockDepth() != 0) {
                    throw bailout("unbalanced monitors");
                }
            }

            @Override
            protected MonitorEnterNode genMonitorEnter(ValueNode x) {
                MonitorIdNode monitorId = currentGraph.add(new MonitorIdNode(frameState.lockDepth()));
                MonitorEnterNode monitorEnter = append(new MonitorEnterNode(x, monitorId));
                frameState.pushLock(x, monitorId);
                return monitorEnter;
            }

            @Override
            protected MonitorExitNode genMonitorExit(ValueNode x, ValueNode escapedReturnValue) {
                MonitorIdNode monitorId = frameState.peekMonitorId();
                ValueNode lockedObject = frameState.popLock();
                if (GraphUtil.originalValue(lockedObject) != GraphUtil.originalValue(x)) {
                    throw bailout(String.format("unbalanced monitors: mismatch at monitorexit, %s != %s", GraphUtil.originalValue(x), GraphUtil.originalValue(lockedObject)));
                }
                MonitorExitNode monitorExit = append(new MonitorExitNode(x, monitorId, escapedReturnValue));
                return monitorExit;
            }

            @Override
            protected void genJsr(int dest) {
                BciBlock successor = currentBlock.getJsrSuccessor();
                assert successor.startBci == dest : successor.startBci + " != " + dest + " @" + bci();
                JsrScope scope = currentBlock.getJsrScope();
                int nextBci = getStream().nextBCI();
                if (!successor.getJsrScope().pop().equals(scope)) {
                    throw new JsrNotSupportedBailout("unstructured control flow (internal limitation)");
                }
                if (successor.getJsrScope().nextReturnAddress() != nextBci) {
                    throw new JsrNotSupportedBailout("unstructured control flow (internal limitation)");
                }
                ConstantNode nextBciNode = getJsrConstant(nextBci);
                frameState.push(Kind.Int, nextBciNode);
                appendGoto(successor);
            }

            @Override
            protected void genRet(int localIndex) {
                BciBlock successor = currentBlock.getRetSuccessor();
                ValueNode local = frameState.loadLocal(localIndex);
                JsrScope scope = currentBlock.getJsrScope();
                int retAddress = scope.nextReturnAddress();
                ConstantNode returnBciNode = getJsrConstant(retAddress);
                LogicNode guard = IntegerEqualsNode.create(local, returnBciNode, constantReflection);
                guard = currentGraph.unique(guard);
                append(new FixedGuardNode(guard, JavaSubroutineMismatch, InvalidateReprofile));
                if (!successor.getJsrScope().equals(scope.pop())) {
                    throw new JsrNotSupportedBailout("unstructured control flow (ret leaves more than one scope)");
                }
                appendGoto(successor);
            }

            private ConstantNode getJsrConstant(long bci) {
                JavaConstant nextBciConstant = new RawConstant(bci);
                Stamp nextBciStamp = StampFactory.forConstant(nextBciConstant);
                ConstantNode nextBciNode = new ConstantNode(nextBciConstant, nextBciStamp);
                return currentGraph.unique(nextBciNode);
            }

            @Override
            protected void genIntegerSwitch(ValueNode value, ArrayList<BciBlock> actualSuccessors, int[] keys, double[] keyProbabilities, int[] keySuccessors) {
                if (value.isConstant()) {
                    JavaConstant constant = (JavaConstant) value.asConstant();
                    int constantValue = constant.asInt();
                    for (int i = 0; i < keys.length; ++i) {
                        if (keys[i] == constantValue) {
                            appendGoto(actualSuccessors.get(keySuccessors[i]));
                            return;
                        }
                    }
                    appendGoto(actualSuccessors.get(keySuccessors[keys.length]));
                } else {
                    this.controlFlowSplit = true;
                    double[] successorProbabilities = successorProbabilites(actualSuccessors.size(), keySuccessors, keyProbabilities);
                    IntegerSwitchNode switchNode = append(new IntegerSwitchNode(value, actualSuccessors.size(), keys, keyProbabilities, keySuccessors));
                    for (int i = 0; i < actualSuccessors.size(); i++) {
                        switchNode.setBlockSuccessor(i, createBlockTarget(successorProbabilities[i], actualSuccessors.get(i), frameState));
                    }
                }
            }

            @Override
            protected ConstantNode appendConstant(JavaConstant constant) {
                assert constant != null;
                return ConstantNode.forConstant(constant, metaAccess, currentGraph);
            }

            @SuppressWarnings("unchecked")
            @Override
            public ValueNode append(ValueNode v) {
                if (v.graph() != null) {
                    // This node was already appended to the graph.
                    return v;
                }
                if (v instanceof ControlSinkNode) {
                    return append((ControlSinkNode) v);
                }
                if (v instanceof ControlSplitNode) {
                    return append((ControlSplitNode) v);
                }
                if (v instanceof FixedWithNextNode) {
                    return append((FixedWithNextNode) v);
                }
                if (v instanceof FloatingNode) {
                    return append((FloatingNode) v);
                }
                throw GraalInternalError.shouldNotReachHere("Can not append Node of type: " + v.getClass().getName());
            }

            public <T extends ControlSinkNode> T append(T fixed) {
                assert !fixed.isAlive() && !fixed.isDeleted() : "instruction should not have been appended yet";
                assert lastInstr.next() == null : "cannot append instruction to instruction which isn't end (" + lastInstr + "->" + lastInstr.next() + ")";
                T added = currentGraph.add(fixed);
                lastInstr.setNext(added);
                lastInstr = null;
                return added;
            }

            public <T extends ControlSplitNode> T append(T fixed) {
                assert !fixed.isAlive() && !fixed.isDeleted() : "instruction should not have been appended yet";
                assert lastInstr.next() == null : "cannot append instruction to instruction which isn't end (" + lastInstr + "->" + lastInstr.next() + ")";
                T added = currentGraph.add(fixed);
                lastInstr.setNext(added);
                lastInstr = null;
                return added;
            }

            public <T extends FixedWithNextNode> T append(T fixed) {
                assert !fixed.isAlive() && !fixed.isDeleted() : "instruction should not have been appended yet";
                assert lastInstr.next() == null : "cannot append instruction to instruction which isn't end (" + lastInstr + "->" + lastInstr.next() + ")";
                T added = currentGraph.add(fixed);
                lastInstr.setNext(added);
                lastInstr = added;
                return added;
            }

            public <T extends FloatingNode> T append(T v) {
                if (v.graph() != null) {
                    return v;
                }
                T added = currentGraph.unique(v);
                return added;
            }

            private Target checkLoopExit(FixedNode target, BciBlock targetBlock, HIRFrameStateBuilder state) {
                if (currentBlock != null && !explodeLoops) {
                    long exits = currentBlock.loops & ~targetBlock.loops;
                    if (exits != 0) {
                        LoopExitNode firstLoopExit = null;
                        LoopExitNode lastLoopExit = null;

                        int pos = 0;
                        ArrayList<BciBlock> exitLoops = new ArrayList<>(Long.bitCount(exits));
                        do {
                            long lMask = 1L << pos;
                            if ((exits & lMask) != 0) {
                                exitLoops.add(blockMap.getLoopHeader(pos));
                                exits &= ~lMask;
                            }
                            pos++;
                        } while (exits != 0);

                        Collections.sort(exitLoops, new Comparator<BciBlock>() {

                            @Override
                            public int compare(BciBlock o1, BciBlock o2) {
                                return Long.bitCount(o2.loops) - Long.bitCount(o1.loops);
                            }
                        });

                        int bci = targetBlock.startBci;
                        if (targetBlock instanceof ExceptionDispatchBlock) {
                            bci = ((ExceptionDispatchBlock) targetBlock).deoptBci;
                        }
                        HIRFrameStateBuilder newState = state.copy();
                        for (BciBlock loop : exitLoops) {
                            LoopBeginNode loopBegin = (LoopBeginNode) getFirstInstruction(loop, this.getCurrentDimension());
                            LoopExitNode loopExit = currentGraph.add(new LoopExitNode(loopBegin));
                            if (lastLoopExit != null) {
                                lastLoopExit.setNext(loopExit);
                            }
                            if (firstLoopExit == null) {
                                firstLoopExit = loopExit;
                            }
                            lastLoopExit = loopExit;
                            Debug.log("Target %s Exits %s, scanning framestates...", targetBlock, loop);
                            newState.insertLoopProxies(loopExit, getEntryState(loop, this.getCurrentDimension()));
                            loopExit.setStateAfter(newState.create(bci));
                        }

                        lastLoopExit.setNext(target);
                        return new Target(firstLoopExit, newState);
                    }
                }
                return new Target(target, state);
            }

            private HIRFrameStateBuilder getEntryState(BciBlock block, int dimension) {
                int id = block.id;
                if (dimension == 0) {
                    return entryStateArray[id];
                } else {
                    return getEntryStateMultiDimension(dimension, id);
                }
            }

            private HIRFrameStateBuilder getEntryStateMultiDimension(int dimension, int id) {
                if (entryStateMatrix != null && dimension - 1 < entryStateMatrix.length) {
                    HIRFrameStateBuilder[] entryStateArrayEntry = entryStateMatrix[dimension - 1];
                    if (entryStateArrayEntry == null) {
                        return null;
                    }
                    return entryStateArrayEntry[id];
                } else {
                    return null;
                }
            }

            private void setEntryState(BciBlock block, int dimension, HIRFrameStateBuilder entryState) {
                int id = block.id;
                if (dimension == 0) {
                    this.entryStateArray[id] = entryState;
                } else {
                    setEntryStateMultiDimension(dimension, entryState, id);
                }
            }

            private void setEntryStateMultiDimension(int dimension, HIRFrameStateBuilder entryState, int id) {
                if (entryStateMatrix == null) {
                    entryStateMatrix = new HIRFrameStateBuilder[4][];
                }
                if (dimension - 1 < entryStateMatrix.length) {
                    // We are within bounds.
                } else {
                    // We are out of bounds.
                    entryStateMatrix = Arrays.copyOf(entryStateMatrix, Math.max(entryStateMatrix.length * 2, dimension));
                }
                if (entryStateMatrix[dimension - 1] == null) {
                    entryStateMatrix[dimension - 1] = new HIRFrameStateBuilder[blockMap.getBlockCount()];
                }
                entryStateMatrix[dimension - 1][id] = entryState;
            }

            private void setFirstInstruction(BciBlock block, int dimension, FixedWithNextNode firstInstruction) {
                int id = block.id;
                if (dimension == 0) {
                    this.firstInstructionArray[id] = firstInstruction;
                } else {
                    setFirstInstructionMultiDimension(dimension, firstInstruction, id);
                }
            }

            private void setFirstInstructionMultiDimension(int dimension, FixedWithNextNode firstInstruction, int id) {
                if (firstInstructionMatrix == null) {
                    firstInstructionMatrix = new FixedWithNextNode[4][];
                }
                if (dimension - 1 < firstInstructionMatrix.length) {
                    // We are within bounds.
                } else {
                    // We are out of bounds.
                    firstInstructionMatrix = Arrays.copyOf(firstInstructionMatrix, Math.max(firstInstructionMatrix.length * 2, dimension));
                }
                if (firstInstructionMatrix[dimension - 1] == null) {
                    firstInstructionMatrix[dimension - 1] = new FixedWithNextNode[blockMap.getBlockCount()];
                }
                firstInstructionMatrix[dimension - 1][id] = firstInstruction;
            }

            private FixedWithNextNode getFirstInstruction(BciBlock block, int dimension) {
                int id = block.id;
                if (dimension == 0) {
                    return firstInstructionArray[id];
                } else {
                    return getFirstInstructionMultiDimension(dimension, id);
                }
            }

            private FixedWithNextNode getFirstInstructionMultiDimension(int dimension, int id) {
                if (firstInstructionMatrix != null && dimension - 1 < firstInstructionMatrix.length) {
                    FixedWithNextNode[] firstInstructionArrayEntry = firstInstructionMatrix[dimension - 1];
                    if (firstInstructionArrayEntry == null) {
                        return null;
                    }
                    return firstInstructionArrayEntry[id];
                } else {
                    return null;
                }
            }

            private FixedNode createTarget(double probability, BciBlock block, HIRFrameStateBuilder stateAfter) {
                assert probability >= 0 && probability <= 1.01 : probability;
                if (isNeverExecutedCode(probability)) {
                    return currentGraph.add(new DeoptimizeNode(InvalidateReprofile, UnreachedCode));
                } else {
                    assert block != null;
                    return createTarget(block, stateAfter);
                }
            }

            private FixedNode createTarget(BciBlock block, HIRFrameStateBuilder state) {
                return createTarget(block, state, false, false);
            }

            private FixedNode createTarget(BciBlock block, HIRFrameStateBuilder state, boolean canReuseInstruction, boolean canReuseState) {
                assert block != null && state != null;
                assert !block.isExceptionEntry || state.stackSize() == 1;

                int operatingDimension = findOperatingDimension(block);

                if (getFirstInstruction(block, operatingDimension) == null) {
                    /*
                     * This is the first time we see this block as a branch target. Create and
                     * return a placeholder that later can be replaced with a MergeNode when we see
                     * this block again.
                     */
                    FixedNode targetNode;
                    if (canReuseInstruction && (block.getPredecessorCount() == 1 || !controlFlowSplit) && !block.isLoopHeader && (currentBlock.loops & ~block.loops) == 0) {
                        setFirstInstruction(block, operatingDimension, lastInstr);
                        lastInstr = null;
                    } else {
                        setFirstInstruction(block, operatingDimension, currentGraph.add(new BeginNode()));
                    }
                    targetNode = getFirstInstruction(block, operatingDimension);
                    Target target = checkLoopExit(targetNode, block, state);
                    FixedNode result = target.fixed;
                    HIRFrameStateBuilder currentEntryState = target.state == state ? (canReuseState ? state : state.copy()) : target.state;
                    setEntryState(block, operatingDimension, currentEntryState);
                    currentEntryState.clearNonLiveLocals(block, liveness, true);

                    Debug.log("createTarget %s: first visit, result: %s", block, targetNode);
                    return result;
                }

                // We already saw this block before, so we have to merge states.
                if (!getEntryState(block, operatingDimension).isCompatibleWith(state)) {
                    throw bailout("stacks do not match; bytecodes would not verify");
                }

                if (getFirstInstruction(block, operatingDimension) instanceof LoopBeginNode) {
                    assert this.explodeLoops || (block.isLoopHeader && currentBlock.getId() >= block.getId()) : "must be backward branch";
                    /*
                     * Backward loop edge. We need to create a special LoopEndNode and merge with
                     * the loop begin node created before.
                     */
                    LoopBeginNode loopBegin = (LoopBeginNode) getFirstInstruction(block, operatingDimension);
                    Target target = checkLoopExit(currentGraph.add(new LoopEndNode(loopBegin)), block, state);
                    FixedNode result = target.fixed;
                    getEntryState(block, operatingDimension).merge(loopBegin, target.state);

                    Debug.log("createTarget %s: merging backward branch to loop header %s, result: %s", block, loopBegin, result);
                    return result;
                }
                assert currentBlock == null || currentBlock.getId() < block.getId() : "must not be backward branch";
                assert getFirstInstruction(block, operatingDimension).next() == null : "bytecodes already parsed for block";

                if (getFirstInstruction(block, operatingDimension) instanceof AbstractBeginNode && !(getFirstInstruction(block, operatingDimension) instanceof AbstractMergeNode)) {
                    /*
                     * This is the second time we see this block. Create the actual MergeNode and
                     * the End Node for the already existing edge.
                     */
                    AbstractBeginNode beginNode = (AbstractBeginNode) getFirstInstruction(block, operatingDimension);

                    // The EndNode for the already existing edge.
                    EndNode end = currentGraph.add(new EndNode());
                    // The MergeNode that replaces the placeholder.
                    AbstractMergeNode mergeNode = currentGraph.add(new MergeNode());
                    FixedNode next = beginNode.next();

                    if (beginNode.predecessor() instanceof ControlSplitNode) {
                        beginNode.setNext(end);
                    } else {
                        beginNode.replaceAtPredecessor(end);
                        beginNode.safeDelete();
                    }

                    mergeNode.addForwardEnd(end);
                    mergeNode.setNext(next);

                    setFirstInstruction(block, operatingDimension, mergeNode);
                }

                AbstractMergeNode mergeNode = (AbstractMergeNode) getFirstInstruction(block, operatingDimension);

                // The EndNode for the newly merged edge.
                EndNode newEnd = currentGraph.add(new EndNode());
                Target target = checkLoopExit(newEnd, block, state);
                FixedNode result = target.fixed;
                getEntryState(block, operatingDimension).merge(mergeNode, target.state);
                mergeNode.addForwardEnd(newEnd);

                Debug.log("createTarget %s: merging state, result: %s", block, result);
                return result;
            }

            private int findOperatingDimension(BciBlock block) {
                if (this.explodeLoops && this.explodeLoopsContext != null && !this.explodeLoopsContext.isEmpty()) {
                    return findOperatingDimensionWithLoopExplosion(block);
                }
                return this.getCurrentDimension();
            }

            private int findOperatingDimensionWithLoopExplosion(BciBlock block) {
                int i;
                for (i = explodeLoopsContext.size() - 1; i >= 0; --i) {
                    ExplodedLoopContext context = explodeLoopsContext.elementAt(i);
                    if (context.header == block) {

                        // We have a hit on our current explosion context loop begin.
                        if (context.targetPeelIteration == -1) {
                            // This is the first hit => allocate a new dimension and at the same
                            // time mark the context loop begin as hit during the current
                            // iteration.
                            context.targetPeelIteration = nextPeelIteration++;
                            if (nextPeelIteration > MaximumLoopExplosionCount.getValue()) {
                                String message = "too many loop explosion interations - does the explosion not terminate for method " + method + "?";
                                if (FailedLoopExplosionIsFatal.getValue()) {
                                    throw new RuntimeException(message);
                                } else {
                                    throw bailout(message);
                                }
                            }
                        }

                        // Operate on the target dimension.
                        return context.targetPeelIteration;
                    } else if (block.getId() > context.header.getId() && block.getId() <= context.header.loopEnd) {
                        // We hit the range of this context.
                        return context.peelIteration;
                    }
                }

                // No dimension found.
                return 0;
            }

            /**
             * Returns a block begin node with the specified state. If the specified probability is
             * 0, the block deoptimizes immediately.
             */
            private AbstractBeginNode createBlockTarget(double probability, BciBlock block, HIRFrameStateBuilder stateAfter) {
                FixedNode target = createTarget(probability, block, stateAfter);
                AbstractBeginNode begin = BeginNode.begin(target);

                assert !(target instanceof DeoptimizeNode && begin instanceof BeginStateSplitNode && ((BeginStateSplitNode) begin).stateAfter() != null) : "We are not allowed to set the stateAfter of the begin node, because we have to deoptimize "
                                + "to a bci _before_ the actual if, so that the interpreter can update the profiling information.";
                return begin;
            }

            private ValueNode synchronizedObject(HIRFrameStateBuilder state, ResolvedJavaMethod target) {
                if (target.isStatic()) {
                    return appendConstant(target.getDeclaringClass().getJavaClass());
                } else {
                    return state.loadLocal(0);
                }
            }

            protected void processBlock(BytecodeParser parser, BciBlock block) {
                // Ignore blocks that have no predecessors by the time their bytecodes are parsed
                int currentDimension = this.getCurrentDimension();
                FixedWithNextNode firstInstruction = getFirstInstruction(block, currentDimension);
                if (firstInstruction == null) {
                    Debug.log("Ignoring block %s", block);
                    return;
                }
                try (Indent indent = Debug.logAndIndent("Parsing block %s  firstInstruction: %s  loopHeader: %b", block, firstInstruction, block.isLoopHeader)) {

                    lastInstr = firstInstruction;
                    frameState = getEntryState(block, currentDimension);
                    parser.setCurrentFrameState(frameState);
                    currentBlock = block;

                    if (firstInstruction instanceof AbstractMergeNode) {
                        setMergeStateAfter(block, firstInstruction);
                    }

                    if (block == blockMap.getReturnBlock()) {
                        handleReturnBlock();
                    } else if (block == blockMap.getUnwindBlock()) {
                        handleUnwindBlock();
                    } else if (block instanceof ExceptionDispatchBlock) {
                        createExceptionDispatch((ExceptionDispatchBlock) block);
                    } else {
                        frameState.setRethrowException(false);
                        iterateBytecodesForBlock(block);
                    }
                }
            }

            private void handleUnwindBlock() {
                if (currentDepth == 0) {
                    frameState.setRethrowException(false);
                    createUnwind();
                } else {
                    ValueNode exception = frameState.apop();
                    this.unwindValue = exception;
                    this.beforeUnwindNode = this.lastInstr;
                }
            }

            private void handleReturnBlock() {
                Kind returnKind = method.getSignature().getReturnKind().getStackKind();
                ValueNode x = returnKind == Kind.Void ? null : frameState.pop(returnKind);
                assert frameState.stackSize() == 0;
                beforeReturn(x);
                this.returnValue = x;
                this.beforeReturnNode = this.lastInstr;
            }

            private void setMergeStateAfter(BciBlock block, FixedWithNextNode firstInstruction) {
                AbstractMergeNode abstractMergeNode = (AbstractMergeNode) firstInstruction;
                if (abstractMergeNode.stateAfter() == null) {
                    int bci = block.startBci;
                    if (block instanceof ExceptionDispatchBlock) {
                        bci = ((ExceptionDispatchBlock) block).deoptBci;
                    }
                    abstractMergeNode.setStateAfter(frameState.create(bci));
                }
            }

            /**
             * Remove loop header without loop ends. This can happen with degenerated loops like
             * this one:
             *
             * <pre>
             * for (;;) {
             *     try {
             *         break;
             *     } catch (UnresolvedException iioe) {
             *     }
             * }
             * </pre>
             */
            private void connectLoopEndToBegin() {
                for (LoopBeginNode begin : currentGraph.getNodes(LoopBeginNode.TYPE)) {
                    if (begin.loopEnds().isEmpty()) {
                        assert begin.forwardEndCount() == 1;
                        currentGraph.reduceDegenerateLoopBegin(begin);
                    } else {
                        GraphUtil.normalizeLoopBegin(begin);
                    }
                }
            }

            private void createUnwind() {
                assert frameState.stackSize() == 1 : frameState;
                ValueNode exception = frameState.apop();
                synchronizedEpilogue(BytecodeFrame.AFTER_EXCEPTION_BCI, null);
                append(new UnwindNode(exception));
            }

            private void synchronizedEpilogue(int bci, ValueNode currentReturnValue) {
                if (method.isSynchronized()) {
                    MonitorExitNode monitorExit = genMonitorExit(methodSynchronizedObject, currentReturnValue);
                    if (currentReturnValue != null) {
                        frameState.push(currentReturnValue.getKind(), currentReturnValue);
                    }
                    monitorExit.setStateAfter(frameState.create(bci));
                    assert !frameState.rethrowException();
                }
            }

            private void createExceptionDispatch(ExceptionDispatchBlock block) {
                assert frameState.stackSize() == 1 : frameState;
                if (block.handler.isCatchAll()) {
                    assert block.getSuccessorCount() == 1;
                    appendGoto(block.getSuccessor(0));
                    return;
                }

                JavaType catchType = block.handler.getCatchType();
                if (graphBuilderConfig.eagerResolving()) {
                    catchType = lookupType(block.handler.catchTypeCPI(), INSTANCEOF);
                }
                boolean initialized = (catchType instanceof ResolvedJavaType);
                if (initialized && graphBuilderConfig.getSkippedExceptionTypes() != null) {
                    ResolvedJavaType resolvedCatchType = (ResolvedJavaType) catchType;
                    for (ResolvedJavaType skippedType : graphBuilderConfig.getSkippedExceptionTypes()) {
                        if (skippedType.isAssignableFrom(resolvedCatchType)) {
                            BciBlock nextBlock = block.getSuccessorCount() == 1 ? blockMap.getUnwindBlock() : block.getSuccessor(1);
                            ValueNode exception = frameState.stackAt(0);
                            FixedNode trueSuccessor = currentGraph.add(new DeoptimizeNode(InvalidateReprofile, UnreachedCode));
                            FixedNode nextDispatch = createTarget(nextBlock, frameState);
                            append(new IfNode(currentGraph.unique(new InstanceOfNode((ResolvedJavaType) catchType, exception, null)), trueSuccessor, nextDispatch, 0));
                            return;
                        }
                    }
                }

                if (initialized) {
                    BciBlock nextBlock = block.getSuccessorCount() == 1 ? blockMap.getUnwindBlock() : block.getSuccessor(1);
                    ValueNode exception = frameState.stackAt(0);
                    CheckCastNode checkCast = currentGraph.add(new CheckCastNode((ResolvedJavaType) catchType, exception, null, false));
                    frameState.apop();
                    frameState.push(Kind.Object, checkCast);
                    FixedNode catchSuccessor = createTarget(block.getSuccessor(0), frameState);
                    frameState.apop();
                    frameState.push(Kind.Object, exception);
                    FixedNode nextDispatch = createTarget(nextBlock, frameState);
                    checkCast.setNext(catchSuccessor);
                    append(new IfNode(currentGraph.unique(new InstanceOfNode((ResolvedJavaType) catchType, exception, null)), checkCast, nextDispatch, 0.5));
                } else {
                    handleUnresolvedExceptionType(catchType);
                }
            }

            private void appendGoto(BciBlock successor) {
                FixedNode targetInstr = createTarget(successor, frameState, true, true);
                if (lastInstr != null && lastInstr != targetInstr) {
                    lastInstr.setNext(targetInstr);
                }
            }

            private boolean isBlockEnd(Node n) {
                return n instanceof ControlSplitNode || n instanceof ControlSinkNode;
            }

            @Override
            protected void iterateBytecodesForBlock(BciBlock block) {
                if (block.isLoopHeader && !explodeLoops) {
                    // Create the loop header block, which later will merge the backward branches of
                    // the loop.
                    controlFlowSplit = true;
                    EndNode preLoopEnd = currentGraph.add(new EndNode());
                    LoopBeginNode loopBegin = currentGraph.add(new LoopBeginNode());
                    lastInstr.setNext(preLoopEnd);
                    // Add the single non-loop predecessor of the loop header.
                    loopBegin.addForwardEnd(preLoopEnd);
                    lastInstr = loopBegin;

                    // Create phi functions for all local variables and operand stack slots.
                    frameState.insertLoopPhis(liveness, block.loopId, loopBegin);
                    loopBegin.setStateAfter(frameState.create(block.startBci));

                    /*
                     * We have seen all forward branches. All subsequent backward branches will
                     * merge to the loop header. This ensures that the loop header has exactly one
                     * non-loop predecessor.
                     */
                    setFirstInstruction(block, this.getCurrentDimension(), loopBegin);
                    /*
                     * We need to preserve the frame state builder of the loop header so that we can
                     * merge values for phi functions, so make a copy of it.
                     */
                    setEntryState(block, this.getCurrentDimension(), frameState.copy());

                    Debug.log("  created loop header %s", loopBegin);
                }
                assert lastInstr.next() == null : "instructions already appended at block " + block;
                Debug.log("  frameState: %s", frameState);

                lastInstr = finishInstruction(lastInstr, frameState);

                int endBCI = stream.endBCI();

                stream.setBCI(block.startBci);
                int bci = block.startBci;
                BytecodesParsed.add(block.endBci - bci);

                while (bci < endBCI) {
                    if (graphBuilderConfig.insertNonSafepointDebugInfo() && lnt != null) {
                        currentLineNumber = lnt.getLineNumber(bci);
                        if (currentLineNumber != previousLineNumber) {
                            append(createInfoPointNode(InfopointReason.LINE_NUMBER));
                            previousLineNumber = currentLineNumber;
                        }
                    }

                    // read the opcode
                    int opcode = stream.currentBC();
                    assert traceState();
                    assert traceInstruction(bci, opcode, bci == block.startBci);
                    if (currentDepth == 0 && bci == entryBCI) {
                        if (block.getJsrScope() != JsrScope.EMPTY_SCOPE) {
                            throw new BailoutException("OSR into a JSR scope is not supported");
                        }
                        EntryMarkerNode x = append(new EntryMarkerNode());
                        frameState.insertProxies(x);
                        x.setStateAfter(frameState.create(bci));
                    }
                    processBytecode(bci, opcode);

                    if (lastInstr == null || isBlockEnd(lastInstr) || lastInstr.next() != null) {
                        break;
                    }

                    stream.next();
                    bci = stream.currentBCI();

                    if (bci > block.endBci) {
                        frameState.clearNonLiveLocals(currentBlock, liveness, false);
                    }
                    if (lastInstr instanceof StateSplit) {
                        if (lastInstr instanceof BeginNode) {
                            // BeginNodes do not need a frame state
                        } else {
                            StateSplit stateSplit = (StateSplit) lastInstr;
                            if (stateSplit.stateAfter() == null) {
                                stateSplit.setStateAfter(frameState.create(bci));
                            }
                        }
                    }
                    lastInstr = finishInstruction(lastInstr, frameState);
                    if (bci < endBCI) {
                        if (bci > block.endBci) {
                            assert !block.getSuccessor(0).isExceptionEntry;
                            assert block.numNormalSuccessors() == 1;
                            // we fell through to the next block, add a goto and break
                            appendGoto(block.getSuccessor(0));
                            break;
                        }
                    }
                }
            }

            /**
             * A hook for derived classes to modify the last instruction or add other instructions.
             *
             * @param instr The last instruction (= fixed node) which was added.
             * @param state The current frame state.
             * @return Returns the (new) last instruction.
             */
            protected FixedWithNextNode finishInstruction(FixedWithNextNode instr, HIRFrameStateBuilder state) {
                return instr;
            }

            private InfopointNode createInfoPointNode(InfopointReason reason) {
                if (graphBuilderConfig.insertFullDebugInfo()) {
                    return new FullInfopointNode(reason, frameState.create(bci()));
                } else {
                    return new SimpleInfopointNode(reason, new BytecodePosition(null, method, bci()));
                }
            }

            private boolean traceState() {
                if (Debug.isEnabled() && Options.TraceBytecodeParserLevel.getValue() >= TRACELEVEL_STATE && Debug.isLogEnabled()) {
                    traceStateHelper();
                }
                return true;
            }

            private void traceStateHelper() {
                Debug.log(String.format("|   state [nr locals = %d, stack depth = %d, method = %s]", frameState.localsSize(), frameState.stackSize(), method));
                for (int i = 0; i < frameState.localsSize(); ++i) {
                    ValueNode value = frameState.localAt(i);
                    Debug.log(String.format("|   local[%d] = %-8s : %s", i, value == null ? "bogus" : value.getKind().getJavaName(), value));
                }
                for (int i = 0; i < frameState.stackSize(); ++i) {
                    ValueNode value = frameState.stackAt(i);
                    Debug.log(String.format("|   stack[%d] = %-8s : %s", i, value == null ? "bogus" : value.getKind().getJavaName(), value));
                }
            }

            @Override
            protected void genIf(ValueNode x, Condition cond, ValueNode y) {
                assert currentBlock.getSuccessorCount() == 2;
                BciBlock trueBlock = currentBlock.getSuccessor(0);
                BciBlock falseBlock = currentBlock.getSuccessor(1);
                if (trueBlock == falseBlock) {
                    // The target block is the same independent of the condition.
                    appendGoto(trueBlock);
                    return;
                }

                ValueNode a = x;
                ValueNode b = y;

                // Check whether the condition needs to mirror the operands.
                if (cond.canonicalMirror()) {
                    a = y;
                    b = x;
                }

                // Create the logic node for the condition.
                LogicNode condition = createLogicNode(cond, a, b);

                // Check whether the condition needs to negate the result.
                boolean negate = cond.canonicalNegate();

                // Remove a logic negation node and fold it into the negate boolean.
                if (condition instanceof LogicNegationNode) {
                    LogicNegationNode logicNegationNode = (LogicNegationNode) condition;
                    negate = !negate;
                    condition = logicNegationNode.getValue();
                }

                if (condition instanceof LogicConstantNode) {
                    genConstantTargetIf(trueBlock, falseBlock, negate, condition);
                } else {
                    if (condition.graph() == null) {
                        condition = currentGraph.unique(condition);
                    }

                    // Need to get probability based on current bci.
                    double probability = branchProbability();

                    if (negate) {
                        BciBlock tmpBlock = trueBlock;
                        trueBlock = falseBlock;
                        falseBlock = tmpBlock;
                        probability = 1 - probability;
                    }

                    if (isNeverExecutedCode(probability)) {
                        append(new FixedGuardNode(condition, UnreachedCode, InvalidateReprofile, true));
                        appendGoto(falseBlock);
                        return;
                    } else if (isNeverExecutedCode(1 - probability)) {
                        append(new FixedGuardNode(condition, UnreachedCode, InvalidateReprofile, false));
                        appendGoto(trueBlock);
                        return;
                    }

                    int oldBci = stream.currentBCI();
                    int trueBlockInt = checkPositiveIntConstantPushed(trueBlock);
                    if (trueBlockInt != -1) {
                        int falseBlockInt = checkPositiveIntConstantPushed(falseBlock);
                        if (falseBlockInt != -1) {
                            if (tryGenConditionalForIf(trueBlock, falseBlock, condition, oldBci, trueBlockInt, falseBlockInt)) {
                                return;
                            }
                        }
                    }

                    this.controlFlowSplit = true;
                    FixedNode trueSuccessor = createTarget(trueBlock, frameState, false, false);
                    FixedNode falseSuccessor = createTarget(falseBlock, frameState, false, true);
                    ValueNode ifNode = genIfNode(condition, trueSuccessor, falseSuccessor, probability);
                    append(ifNode);
                }
            }

            private boolean tryGenConditionalForIf(BciBlock trueBlock, BciBlock falseBlock, LogicNode condition, int oldBci, int trueBlockInt, int falseBlockInt) {
                if (gotoOrFallThroughAfterConstant(trueBlock) && gotoOrFallThroughAfterConstant(falseBlock) && trueBlock.getSuccessor(0) == falseBlock.getSuccessor(0)) {
                    genConditionalForIf(trueBlock, condition, oldBci, trueBlockInt, falseBlockInt, false);
                    return true;
                } else if (this.currentDepth != 0 && returnAfterConstant(trueBlock) && returnAfterConstant(falseBlock)) {
                    genConditionalForIf(trueBlock, condition, oldBci, trueBlockInt, falseBlockInt, true);
                    return true;
                }
                return false;
            }

            private void genConditionalForIf(BciBlock trueBlock, LogicNode condition, int oldBci, int trueBlockInt, int falseBlockInt, boolean genReturn) {
                ConstantNode trueValue = currentGraph.unique(ConstantNode.forInt(trueBlockInt));
                ConstantNode falseValue = currentGraph.unique(ConstantNode.forInt(falseBlockInt));
                ValueNode conditionalNode = ConditionalNode.create(condition, trueValue, falseValue);
                if (conditionalNode.graph() == null) {
                    conditionalNode = currentGraph.addOrUnique(conditionalNode);
                }
                if (genReturn) {
                    this.genReturn(conditionalNode);
                } else {
                    frameState.push(Kind.Int, conditionalNode);
                    appendGoto(trueBlock.getSuccessor(0));
                    stream.setBCI(oldBci);
                }
            }

            private LogicNode createLogicNode(Condition cond, ValueNode a, ValueNode b) {
                LogicNode condition;
                assert !a.getKind().isNumericFloat();
                if (cond == Condition.EQ || cond == Condition.NE) {
                    if (a.getKind() == Kind.Object) {
                        condition = genObjectEquals(a, b);
                    } else {
                        condition = genIntegerEquals(a, b);
                    }
                } else {
                    assert a.getKind() != Kind.Object && !cond.isUnsigned();
                    condition = genIntegerLessThan(a, b);
                }
                return condition;
            }

            private void genConstantTargetIf(BciBlock trueBlock, BciBlock falseBlock, boolean negate, LogicNode condition) {
                LogicConstantNode constantLogicNode = (LogicConstantNode) condition;
                boolean value = constantLogicNode.getValue();
                if (negate) {
                    value = !value;
                }
                BciBlock nextBlock = falseBlock;
                if (value) {
                    nextBlock = trueBlock;
                }
                appendGoto(nextBlock);
            }

            private int checkPositiveIntConstantPushed(BciBlock block) {
                stream.setBCI(block.startBci);
                int currentBC = stream.currentBC();
                if (currentBC >= Bytecodes.ICONST_0 && currentBC <= Bytecodes.ICONST_5) {
                    int constValue = currentBC - Bytecodes.ICONST_0;
                    return constValue;
                }
                return -1;
            }

            private boolean gotoOrFallThroughAfterConstant(BciBlock block) {
                stream.setBCI(block.startBci);
                int currentBCI = stream.nextBCI();
                stream.setBCI(currentBCI);
                int currentBC = stream.currentBC();
                return stream.currentBCI() > block.endBci || currentBC == Bytecodes.GOTO || currentBC == Bytecodes.GOTO_W;
            }

            private boolean returnAfterConstant(BciBlock block) {
                stream.setBCI(block.startBci);
                int currentBCI = stream.nextBCI();
                stream.setBCI(currentBCI);
                int currentBC = stream.currentBC();
                return currentBC == Bytecodes.IRETURN;
            }

            public StampProvider getStampProvider() {
                return stampProvider;
            }

            public MetaAccessProvider getMetaAccess() {
                return metaAccess;
            }

            public Assumptions getAssumptions() {
                return currentGraph.getAssumptions();
            }

            public void push(Kind kind, ValueNode value) {
                assert kind == kind.getStackKind();
                frameState.push(kind, value);
            }

            private int getCurrentDimension() {
                if (this.explodeLoopsContext == null || this.explodeLoopsContext.isEmpty()) {
                    return 0;
                } else {
                    return this.explodeLoopsContext.peek().peelIteration;
                }
            }

            public ConstantReflectionProvider getConstantReflection() {
                return constantReflection;
            }

            public SnippetReflectionProvider getSnippetReflection() {
                return snippetReflectionProvider;
            }

            public boolean parsingReplacement() {
                return parsingReplacement;
            }

            public StructuredGraph getGraph() {
                return currentGraph;
            }

            public GuardingNode getCurrentBlockGuard() {
                return (GuardingNode) getFirstInstruction(currentBlock, getCurrentDimension());
            }

            @Override
            public String toString() {
                return method.format("%H.%n(%p)@") + bci();
            }

            public BailoutException bailout(String string) {
                FrameState currentFrameState = this.frameState.create(bci());
                StackTraceElement[] elements = GraphUtil.approxSourceStackTraceElement(currentFrameState);
                BailoutException bailout = new BailoutException(string);
                throw GraphUtil.createBailoutException(string, bailout, elements);
            }
        }
    }

    static String nSpaces(int n) {
        return n == 0 ? "" : format("%" + n + "s", "");
    }
}
