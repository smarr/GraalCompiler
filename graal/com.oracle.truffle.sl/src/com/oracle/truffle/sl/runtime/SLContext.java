/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.runtime;

import java.io.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.sl.*;
import com.oracle.truffle.sl.builtins.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.nodes.local.*;
import com.oracle.truffle.sl.parser.*;

/**
 * The run-time state of SL during execution. One context is instantiated before any source code is
 * parsed, and this context is passed around to all methods that need access to it. For example, the
 * context is used during {@link SLNodeFactory parsing} and by {@link SLBuiltinNode#getContext()
 * builtin functions}.
 * <p>
 * It would be an error to have two different context instances during the execution of one script.
 * However, if two separate scripts run in one Java VM at the same time, they have a different
 * context. Therefore, the context is not a singleton.
 */
public final class SLContext extends ExecutionContext {
    private final BufferedReader input;
    private final PrintStream output;
    private final SLFunctionRegistry functionRegistry;
    private SourceCallback sourceCallback = null;

    public SLContext(BufferedReader input, PrintStream output) {
        this.input = input;
        this.output = output;
        this.functionRegistry = new SLFunctionRegistry();
        installBuiltins();
    }

    @Override
    public String getLanguageShortName() {
        return "Simple";
    }

    @Override
    public void setSourceCallback(SourceCallback sourceCallback) {
        this.sourceCallback = sourceCallback;
    }

    /**
     * Returns the default input, i.e., the source for the {@link SLReadlnBuiltin}. To allow unit
     * testing, we do not use {@link System#in} directly.
     */
    public BufferedReader getInput() {
        return input;
    }

    /**
     * The default default, i.e., the output for the {@link SLPrintlnBuiltin}. To allow unit
     * testing, we do not use {@link System#out} directly.
     */
    public PrintStream getOutput() {
        return output;
    }

    /**
     * Returns the registry of all functions that are currently defined.
     */
    public SLFunctionRegistry getFunctionRegistry() {
        return functionRegistry;
    }

    public SourceCallback getSourceCallback() {
        return sourceCallback;
    }

    /**
     * Adds all builtin functions to the {@link SLFunctionRegistry}. This method lists all
     * {@link SLBuiltinNode builtin implementation classes}.
     */
    private void installBuiltins() {
        installBuiltin(SLReadlnBuiltinFactory.getInstance());
        installBuiltin(SLPrintlnBuiltinFactory.getInstance());
        installBuiltin(SLNanoTimeBuiltinFactory.getInstance());
        installBuiltin(SLDefineFunctionBuiltinFactory.getInstance());
        installBuiltin(SLStackTraceBuiltinFactory.getInstance());
        installBuiltin(SLHelloEqualsWorldBuiltinFactory.getInstance());
    }

    private void installBuiltin(NodeFactory<? extends SLBuiltinNode> factory) {
        /*
         * The builtin node factory is a class that is automatically generated by the Truffle DSL.
         * The signature returned by the factory reflects the signature of the @Specialization
         * methods in the builtin classes.
         */
        int argumentCount = factory.getExecutionSignature().size();
        SLExpressionNode[] argumentNodes = new SLExpressionNode[argumentCount];
        /*
         * Builtin functions are like normal functions, i.e., the arguments are passed in as an
         * Object[] array encapsulated in SLArguments. A SLReadArgumentNode extracts a parameter
         * from this array.
         */
        for (int i = 0; i < argumentCount; i++) {
            argumentNodes[i] = new SLReadArgumentNode(null, i);
        }
        /* Instantiate the builtin node. This node performs the actual functionality. */
        SLBuiltinNode builtinBodyNode = factory.createNode(argumentNodes, this);
        /* The name of the builtin function is specified via an annotation on the node class. */
        String name = builtinBodyNode.getClass().getAnnotation(NodeInfo.class).shortName();
        /* Wrap the builtin in a RootNode. Truffle requires all AST to start with a RootNode. */
        SLRootNode rootNode = new SLRootNode(this, new FrameDescriptor(), builtinBodyNode, name);

        /* Register the builtin function in our function registry. */
        getFunctionRegistry().register(name, rootNode);
    }

    /**
     * This function will parse the given source code, parse the code using the {@link Parser}, and
     * then execute the function named main. To use this method with instrumentation,
     * setASTNodeProber must have been already called. There is currently no guard to check if this
     * is the case. <br/>
     * Due to the experimental nature of the instrumentation framework, the parse that happens in
     * this method will remove any previously added instrumentation.
     *
     * @param source The {@link Source} to execute.
     */
    public void executeMain(Source source) {

        if (sourceCallback != null) {
            sourceCallback.startLoading(source);
        }

        Parser.parseSL(this, source);

        if (sourceCallback != null) {
            sourceCallback.endLoading(source);
        }

        SLFunction main = getFunctionRegistry().lookup("main");
        if (main.getCallTarget() == null) {
            throw new SLException("No function main() defined in SL source file.");
        }
        main.getCallTarget().call();
    }
}
