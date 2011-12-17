/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.cri.bytecode;

/**
 * A utility for processing {@link Bytecodes#LOOKUPSWITCH} bytecodes.
 */
public class BytecodeLookupSwitch extends BytecodeSwitch {
    private static final int OFFSET_TO_NUMBER_PAIRS = 4;
    private static final int OFFSET_TO_FIRST_PAIR_MATCH = 8;
    private static final int OFFSET_TO_FIRST_PAIR_OFFSET = 12;
    private static final int PAIR_SIZE = 8;

    /**
     * Constructor for a {@link BytecodeStream}.
     * @param stream the {@code BytecodeStream} containing the switch instruction
     * @param bci the index in the stream of the switch instruction
     */
    public BytecodeLookupSwitch(BytecodeStream stream, int bci) {
        super(stream, bci);
    }

    /**
     * Constructor for a bytecode array.
     * @param code the bytecode array containing the switch instruction.
     * @param bci the index in the array of the switch instruction
     */
    public BytecodeLookupSwitch(byte[] code, int bci) {
        super(code, bci);
    }

    @Override
    public int defaultOffset() {
        return readWord(alignedBci);
    }

    @Override
    public int offsetAt(int i) {
        return readWord(alignedBci + OFFSET_TO_FIRST_PAIR_OFFSET + PAIR_SIZE * i);
    }

    @Override
    public int keyAt(int i) {
        return readWord(alignedBci + OFFSET_TO_FIRST_PAIR_MATCH + PAIR_SIZE * i);
    }

    @Override
    public int numberOfCases() {
        return readWord(alignedBci + OFFSET_TO_NUMBER_PAIRS);
    }

    @Override
    public int size() {
        return alignedBci + OFFSET_TO_FIRST_PAIR_MATCH + PAIR_SIZE * numberOfCases() - bci;
    }
}
