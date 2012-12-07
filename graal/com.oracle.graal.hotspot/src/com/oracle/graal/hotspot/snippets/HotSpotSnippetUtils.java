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
package com.oracle.graal.hotspot.snippets;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.snippets.Snippet.Fold;
import com.oracle.graal.snippets.*;

//JaCoCo Exclude

/**
 * A collection of methods used in HotSpot snippets.
 */
public class HotSpotSnippetUtils {

    public static HotSpotVMConfig config() {
        return HotSpotGraalRuntime.getInstance().getConfig();
    }

    @Fold
    public static boolean verifyOops() {
        return config().verifyOops;
    }

    @Fold
    public static int threadTlabTopOffset() {
        return config().threadTlabTopOffset;
    }

    @Fold
    public static int threadTlabEndOffset() {
        return config().threadTlabEndOffset;
    }

    @Fold
    public static Kind wordKind() {
        return HotSpotGraalRuntime.getInstance().getTarget().wordKind;
    }

    @Fold
    public static Register threadRegister() {
        return HotSpotGraalRuntime.getInstance().getRuntime().threadRegister();
    }

    @Fold
    public static Register stackPointerRegister() {
        return HotSpotGraalRuntime.getInstance().getRuntime().stackPointerRegister();
    }

    @Fold
    public static int wordSize() {
        return HotSpotGraalRuntime.getInstance().getTarget().wordSize;
    }

    @Fold
    public static int pageSize() {
        return HotSpotGraalRuntime.getInstance().getTarget().pageSize;
    }

    @Fold
    public static int prototypeMarkWordOffset() {
        return config().prototypeMarkWordOffset;
    }

    @Fold
    public static int markOffset() {
        return config().markOffset;
    }

    @Fold
    public static int unlockedMask() {
        return config().unlockedMask;
    }

    /**
     * Mask for a biasable, locked or unlocked mark word.
     * <pre>
     * +----------------------------------+-+-+
     * |                                 1|1|1|
     * +----------------------------------+-+-+
     * </pre>
     *
     */
    @Fold
    public static int biasedLockMaskInPlace() {
        return config().biasedLockMaskInPlace;
    }

    @Fold
    public static int epochMaskInPlace() {
        return config().epochMaskInPlace;
    }

    /**
     * Pattern for a biasable, unlocked mark word.
     * <pre>
     * +----------------------------------+-+-+
     * |                                 1|0|1|
     * +----------------------------------+-+-+
     * </pre>
     *
     */
    @Fold
    public static int biasedLockPattern() {
        return config().biasedLockPattern;
    }

    @Fold
    public static int ageMaskInPlace() {
        return config().ageMaskInPlace;
    }

    @Fold
    public static int hubOffset() {
        return config().hubOffset;
    }

    @Fold
    public static int metaspaceArrayLengthOffset() {
        return config().metaspaceArrayLengthOffset;
    }

    @Fold
    public static int metaspaceArrayBaseOffset() {
        return config().metaspaceArrayBaseOffset;
    }

    @Fold
    public static int arrayLengthOffset() {
        return config().arrayLengthOffset;
    }

    @Fold
    public static int arrayBaseOffset(Kind elementKind) {
        return HotSpotRuntime.getArrayBaseOffset(elementKind);
    }

    @Fold
    public static int arrayIndexScale(Kind elementKind) {
        return HotSpotRuntime.getArrayIndexScale(elementKind);
    }

    @Fold
    public static int cardTableShift() {
        return config().cardtableShift;
    }

    @Fold
    public static long cardTableStart() {
        return config().cardtableStartAddress;
    }

    @Fold
    public static int superCheckOffsetOffset() {
        return config().superCheckOffsetOffset;
    }

    @Fold
    public static int secondarySuperCacheOffset() {
        return config().secondarySuperCacheOffset;
    }

    @Fold
    public static int secondarySupersOffset() {
        return config().secondarySupersOffset;
    }

    @Fold
    public static int lockDisplacedMarkOffset() {
        return config().basicLockDisplacedHeaderOffset;
    }

    @Fold
    public static boolean useBiasedLocking() {
        return config().useBiasedLocking;
    }

    /**
     * Loads the hub from a object, null checking it first.
     */
    public static Word loadHub(Object object) {
        return loadHubIntrinsic(object, wordKind());
    }

    public static Object verifyOop(Object object) {
        if (verifyOops()) {
            VerifyOopStubCall.call(object);
        }
        return object;
    }

    /**
     * Gets the value of the stack pointer register as a Word.
     */
    public static Word stackPointer() {
        return HotSpotSnippetUtils.registerAsWord(stackPointerRegister());
    }

    /**
     * Gets the value of the thread register as a Word.
     */
    public static Word thread() {
        return HotSpotSnippetUtils.registerAsWord(threadRegister());
    }

    public static int loadIntFromWord(Word address, int offset) {
        Integer value = UnsafeLoadNode.load(address, 0, offset, Kind.Int);
        return value;
    }

    public static Word loadWordFromWord(Word address, int offset) {
        return loadWordFromWordIntrinsic(address, 0, offset, wordKind());
    }

    public static Word loadWordFromObject(Object object, int offset) {
        return loadWordFromObjectIntrinsic(object, 0, offset, wordKind());
    }

    @NodeIntrinsic(value = RegisterNode.class, setStampFromReturnType = true)
    public static native Word registerAsWord(@ConstantNodeParameter Register register);

    @NodeIntrinsic(value = UnsafeLoadNode.class, setStampFromReturnType = true)
    private static native Word loadWordFromObjectIntrinsic(Object object, @ConstantNodeParameter int displacement, long offset, @ConstantNodeParameter Kind wordKind);

    @NodeIntrinsic(value = UnsafeLoadNode.class, setStampFromReturnType = true)
    private static native Word loadWordFromWordIntrinsic(Word address, @ConstantNodeParameter int displacement, long offset, @ConstantNodeParameter Kind wordKind);

    @NodeIntrinsic(value = LoadHubNode.class, setStampFromReturnType = true)
    static native Word loadHubIntrinsic(Object object, @ConstantNodeParameter Kind word);

    static {
        assert arrayIndexScale(Kind.Byte) == 1;
        assert arrayIndexScale(Kind.Boolean) == 1;
        assert arrayIndexScale(Kind.Char) == 2;
        assert arrayIndexScale(Kind.Short) == 2;
        assert arrayIndexScale(Kind.Int) == 4;
        assert arrayIndexScale(Kind.Long) == 8;
        assert arrayIndexScale(Kind.Float) == 4;
        assert arrayIndexScale(Kind.Double) == 8;
    }
}
