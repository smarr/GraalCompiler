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
package com.oracle.graal.hotspot;

import static com.oracle.graal.lir.LIRValueUtil.isJavaConstant;
import static jdk.internal.jvmci.code.ValueUtil.asRegister;
import static jdk.internal.jvmci.code.ValueUtil.asStackSlot;
import static jdk.internal.jvmci.code.ValueUtil.isRegister;

import java.util.ArrayList;

import jdk.internal.jvmci.code.Location;
import jdk.internal.jvmci.code.ReferenceMap;
import jdk.internal.jvmci.code.StackSlot;
import jdk.internal.jvmci.common.JVMCIError;
import jdk.internal.jvmci.hotspot.HotSpotReferenceMap;
import jdk.internal.jvmci.meta.LIRKind;
import jdk.internal.jvmci.meta.PlatformKind;
import jdk.internal.jvmci.meta.Value;

import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.framemap.ReferenceMapBuilder;

public final class HotSpotReferenceMapBuilder extends ReferenceMapBuilder {

    private int maxRegisterSize;

    private final ArrayList<Value> objectValues;
    private int objectCount;

    private final int totalFrameSize;

    public HotSpotReferenceMapBuilder(int totalFrameSize) {
        this.objectValues = new ArrayList<>();
        this.objectCount = 0;

        this.totalFrameSize = totalFrameSize;
    }

    @Override
    public void addLiveValue(Value v) {
        if (isJavaConstant(v)) {
            return;
        }
        LIRKind lirKind = v.getLIRKind();
        if (!lirKind.isValue()) {
            objectValues.add(v);
            if (lirKind.isUnknownReference()) {
                objectCount++;
            } else {
                objectCount += lirKind.getReferenceCount();
            }
        }
        if (isRegister(v)) {
            int size = lirKind.getPlatformKind().getSizeInBytes();
            if (size > maxRegisterSize) {
                maxRegisterSize = size;
            }
        }
    }

    @Override
    public ReferenceMap finish(LIRFrameState state) {
        Location[] objects = new Location[objectCount];
        Location[] derivedBase = new Location[objectCount];
        int[] sizeInBytes = new int[objectCount];

        int idx = 0;
        for (Value obj : objectValues) {
            LIRKind kind = obj.getLIRKind();
            int bytes = bytesPerElement(kind);
            if (kind.isUnknownReference()) {
                throw JVMCIError.shouldNotReachHere("unknown reference alive across safepoint");
            } else {
                Location base = null;
                if (kind.isDerivedReference()) {
                    Variable baseVariable = (Variable) kind.getDerivedReferenceBase();
                    Value baseValue = state.getLiveBasePointers().get(baseVariable.index);
                    assert baseValue.getPlatformKind().getVectorLength() == 1 && baseValue.getLIRKind().isReference(0) && !baseValue.getLIRKind().isDerivedReference();
                    base = toLocation(baseValue, 0);
                }

                for (int i = 0; i < kind.getPlatformKind().getVectorLength(); i++) {
                    if (kind.isReference(i)) {
                        objects[idx] = toLocation(obj, i * bytes);
                        derivedBase[idx] = base;
                        sizeInBytes[idx] = bytes;
                        idx++;
                    }
                }
            }
        }

        return new HotSpotReferenceMap(objects, derivedBase, sizeInBytes, maxRegisterSize);
    }

    private static int bytesPerElement(LIRKind kind) {
        PlatformKind platformKind = kind.getPlatformKind();
        return platformKind.getSizeInBytes() / platformKind.getVectorLength();
    }

    private Location toLocation(Value v, int offset) {
        if (isRegister(v)) {
            return Location.subregister(asRegister(v), offset);
        } else {
            StackSlot s = asStackSlot(v);
            return Location.stack(s.getOffset(totalFrameSize) + offset);
        }
    }
}
