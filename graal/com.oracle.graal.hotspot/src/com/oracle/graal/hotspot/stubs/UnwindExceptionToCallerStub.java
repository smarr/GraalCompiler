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
package com.oracle.graal.hotspot.stubs;

import static com.oracle.graal.hotspot.nodes.JumpToExceptionHandlerInCallerNode.jumpToExceptionHandlerInCaller;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.registerAsWord;
import static com.oracle.graal.hotspot.stubs.ExceptionHandlerStub.checkExceptionNotNull;
import static com.oracle.graal.hotspot.stubs.ExceptionHandlerStub.checkNoExceptionInThread;
import static com.oracle.graal.hotspot.stubs.StubUtil.cAssertionsEnabled;
import static com.oracle.graal.hotspot.stubs.StubUtil.decipher;
import static com.oracle.graal.hotspot.stubs.StubUtil.newDescriptor;
import static com.oracle.graal.hotspot.stubs.StubUtil.printf;
import jdk.internal.jvmci.code.Register;

import com.oracle.graal.api.replacements.Fold;
import com.oracle.graal.compiler.common.spi.ForeignCallDescriptor;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.HotSpotForeignCallLinkage;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.hotspot.nodes.StubForeignCallNode;
import com.oracle.graal.nodes.UnwindNode;
import com.oracle.graal.replacements.Snippet;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.word.Pointer;
import com.oracle.graal.word.Word;

/**
 * Stub called by an {@link UnwindNode}. This stub executes in the frame of the method throwing an
 * exception and completes by jumping to the exception handler in the calling frame.
 */
public class UnwindExceptionToCallerStub extends SnippetStub {

    public UnwindExceptionToCallerStub(HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super("unwindExceptionToCaller", providers, linkage);
    }

    /**
     * The current frame is unwound by this stub. Therefore, it does not need to save any registers
     * as HotSpot uses a caller save convention.
     */
    @Override
    public boolean preservesRegisters() {
        return false;
    }

    @Override
    protected Object getConstantParameterValue(int index, String name) {
        assert index == 2;
        return providers.getRegisters().getThreadRegister();
    }

    @Snippet
    private static void unwindExceptionToCaller(Object exception, Word returnAddress, @ConstantParameter Register threadRegister) {
        Pointer exceptionOop = Word.objectToTrackedPointer(exception);
        if (logging()) {
            printf("unwinding exception %p (", exceptionOop.rawValue());
            decipher(exceptionOop.rawValue());
            printf(") at %p (", returnAddress.rawValue());
            decipher(returnAddress.rawValue());
            printf(")\n");
        }
        Word thread = registerAsWord(threadRegister);
        checkNoExceptionInThread(thread, assertionsEnabled());
        checkExceptionNotNull(assertionsEnabled(), exception);

        Word handlerInCallerPc = exceptionHandlerForReturnAddress(EXCEPTION_HANDLER_FOR_RETURN_ADDRESS, thread, returnAddress);

        if (logging()) {
            printf("handler for exception %p at return address %p is at %p (", exceptionOop.rawValue(), returnAddress.rawValue(), handlerInCallerPc.rawValue());
            decipher(handlerInCallerPc.rawValue());
            printf(")\n");
        }

        jumpToExceptionHandlerInCaller(handlerInCallerPc, exception, returnAddress);
    }

    @Fold
    private static boolean logging() {
        return Boolean.getBoolean("graal.logUnwindExceptionToCallerStub");
    }

    /**
     * Determines if either Java assertions are enabled for {@link UnwindExceptionToCallerStub} or
     * if this is a HotSpot build where the ASSERT mechanism is enabled.
     * <p>
     * This first check relies on the per-class assertion status which is why this method must be in
     * this class.
     */
    @Fold
    @SuppressWarnings("all")
    private static boolean assertionsEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled || cAssertionsEnabled();
    }

    public static final ForeignCallDescriptor EXCEPTION_HANDLER_FOR_RETURN_ADDRESS = newDescriptor(UnwindExceptionToCallerStub.class, "exceptionHandlerForReturnAddress", Word.class, Word.class,
                    Word.class);

    @NodeIntrinsic(value = StubForeignCallNode.class, setStampFromReturnType = true)
    public static native Word exceptionHandlerForReturnAddress(@ConstantNodeParameter ForeignCallDescriptor exceptionHandlerForReturnAddress, Word thread, Word returnAddress);
}
