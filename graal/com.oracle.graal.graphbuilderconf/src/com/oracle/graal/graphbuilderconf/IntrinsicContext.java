/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graphbuilderconf;

import static com.oracle.graal.graphbuilderconf.IntrinsicContext.CompilationContext.INLINE_AFTER_PARSING;
import static com.oracle.graal.graphbuilderconf.IntrinsicContext.CompilationContext.ROOT_COMPILATION;
import static jdk.internal.jvmci.code.BytecodeFrame.AFTER_BCI;
import static jdk.internal.jvmci.code.BytecodeFrame.BEFORE_BCI;
import static jdk.internal.jvmci.code.BytecodeFrame.INVALID_FRAMESTATE_BCI;
import jdk.internal.jvmci.meta.ResolvedJavaMethod;

import com.oracle.graal.nodes.AbstractMergeNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.nodes.StateSplit;
import com.oracle.graal.nodes.StructuredGraph;

/**
 * An intrinsic is a substitute implementation of a Java method (or a bytecode in the case of
 * snippets) that is itself implemented in Java. This interface provides information about the
 * intrinsic currently being processed by the graph builder.
 *
 * When in the scope of an intrinsic, the graph builder does not check the value kinds flowing
 * through the JVM state since intrinsics can employ non-Java kinds to represent values such as raw
 * machine words and pointers.
 */
public class IntrinsicContext {

    /**
     * Gets the method being intrinsified.
     */
    final ResolvedJavaMethod method;

    /**
     * Gets the method providing the intrinsic implementation.
     */
    final ResolvedJavaMethod intrinsic;

    public ResolvedJavaMethod getOriginalMethod() {
        return method;
    }

    public ResolvedJavaMethod getIntrinsicMethod() {
        return intrinsic;
    }

    /**
     * Determines if a call within the compilation scope of this intrinsic represents a call to the
     * {@linkplain #getOriginalMethod() original} method. This denotes the path where a partial
     * intrinsification falls back to the original method.
     */
    public boolean isCallToOriginal(ResolvedJavaMethod targetMethod) {
        return method.equals(targetMethod) || intrinsic.equals(targetMethod);
    }

    final CompilationContext compilationContext;

    public IntrinsicContext(ResolvedJavaMethod method, ResolvedJavaMethod intrinsic, CompilationContext compilationContext) {
        this.method = method;
        this.intrinsic = intrinsic;
        this.compilationContext = compilationContext;
        assert !isCompilationRoot() || method.hasBytecodes() : "Cannot root compile intrinsic for native or abstract method " + method.format("%H.%n(%p)");
    }

    public boolean isPostParseInlined() {
        return compilationContext.equals(INLINE_AFTER_PARSING);
    }

    public boolean isCompilationRoot() {
        return compilationContext.equals(ROOT_COMPILATION);
    }

    /**
     * Denotes the compilation context in which an intrinsic is being parsed.
     */
    public enum CompilationContext {
        /**
         * An intrinsic is being processed when parsing an invoke bytecode that calls the
         * intrinsified method.
         */
        INLINE_DURING_PARSING,

        /**
         * An intrinsic is being processed when inlining an {@link Invoke} in an existing graph.
         */
        INLINE_AFTER_PARSING,

        /**
         * An intrinsic is the root of compilation.
         */
        ROOT_COMPILATION
    }

    /**
     * Models the state of a graph in terms of {@link StateSplit#hasSideEffect() side effects} that
     * are control flow predecessors of the current point in a graph.
     */
    public interface SideEffectsState {

        /**
         * Determines if the current program point is preceded by one or more side effects.
         */
        boolean isAfterSideEffect();

        /**
         * Gets the side effects preceding the current program point.
         */
        Iterable<StateSplit> sideEffects();

        /**
         * Records a side effect for the current program point.
         */
        void addSideEffect(StateSplit sideEffect);
    }

    public FrameState createFrameState(StructuredGraph graph, SideEffectsState sideEffects, StateSplit forStateSplit) {
        assert forStateSplit != graph.start();
        if (forStateSplit.hasSideEffect()) {
            if (sideEffects.isAfterSideEffect()) {
                // Only the last side effect on any execution path in a replacement
                // can inherit the stateAfter of the replaced node
                FrameState invalid = graph.add(new FrameState(INVALID_FRAMESTATE_BCI));
                for (StateSplit lastSideEffect : sideEffects.sideEffects()) {
                    lastSideEffect.setStateAfter(invalid);
                }
            }
            sideEffects.addSideEffect(forStateSplit);
            return graph.add(new FrameState(AFTER_BCI));
        } else {
            if (forStateSplit instanceof AbstractMergeNode) {
                // Merge nodes always need a frame state
                if (sideEffects.isAfterSideEffect()) {
                    // A merge after one or more side effects
                    return graph.add(new FrameState(AFTER_BCI));
                } else {
                    // A merge before any side effects
                    return graph.add(new FrameState(BEFORE_BCI));
                }
            } else {
                // Other non-side-effects do not need a state
                return null;
            }
        }
    }

    @Override
    public String toString() {
        return "Intrinsic{original: " + method.format("%H.%n(%p)") + ", intrinsic: " + intrinsic.format("%H.%n(%p)") + ", context: " + compilationContext + "}";
    }
}
