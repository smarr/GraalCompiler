/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.code;

import static com.oracle.graal.api.code.ValueUtil.*;

import com.oracle.graal.api.meta.*;


/**
 * A calling convention describes the locations in which the arguments for a call are placed
 * and the location in which the return value is placed if the call is not void.
 */
public class CallingConvention {

    /**
     * Constants denoting the type of a call for which a calling convention is requested.
     */
    public enum Type {
        /**
         * A request for the outgoing argument locations at a call site to Java code.
         */
        JavaCall(true),

        /**
         * A request for the incoming argument locations.
         */
        JavaCallee(false),

        /**
         * A request for the outgoing argument locations at a call site to the runtime (which may be Java or native code).
         */
        RuntimeCall(true),

        /**
         * A request for the outgoing argument locations at a call site to
         * external native code that complies with the platform ABI.
         */
        NativeCall(true);

        /**
         * Determines if this is a request for the outgoing argument locations at a call site.
         */
        public final boolean out;

        public static final Type[] VALUES = values();

        private Type(boolean out) {
            this.out = out;
        }
    }

    /**
     * The amount of stack space (in bytes) required for the stack-based arguments of the call.
     */
    private final int stackSize;

    private final Value returnLocation;

    /**
     * The locations in which the arguments are placed. This array ordered by argument index.
     */
    private final Value[] argumentLocations;

    public CallingConvention(int stackSize, Value returnLocation, Value... locations) {
        this.argumentLocations = locations;
        this.stackSize = stackSize;
        this.returnLocation = returnLocation;
        assert verify();
    }

    /**
     * @return the location for the return value or {@link Value#IllegalValue} if a void call
     */
    public Value getReturn() {
        return returnLocation;
    }

    /**
     * @return the location for the {@code index}'th argument
     */
    public Value getArgument(int index) {
        return argumentLocations[index];
    }

    /**
     * @return the amount of stack space (in bytes) required for the stack-based arguments of the call.
     */
    public int getStackSize() {
        return stackSize;
    }

    /**
     * @return the number of locations required for the arguments
     */
    public int getArgumentCount() {
        return argumentLocations.length;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("CallingConvention[");
        String sep = "";
        for (Value op : argumentLocations) {
            result.append(sep).append(op);
            sep = ", ";
        }
        if (returnLocation != Value.IllegalValue) {
            result.append(" -> ").append(returnLocation);
        }
        result.append("]");
        return result.toString();
    }

    private boolean verify() {
        for (int i = 0; i < argumentLocations.length; i++) {
            Value location = argumentLocations[i];
            assert isStackSlot(location) || isRegister(location);
        }
        return true;
    }
}
