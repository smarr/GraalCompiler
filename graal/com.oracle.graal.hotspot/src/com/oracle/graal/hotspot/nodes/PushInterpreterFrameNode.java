/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes;

import jdk.internal.jvmci.meta.Value;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.hotspot.HotSpotLIRGenerator;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import com.oracle.graal.word.Word;

/**
 * A call to the runtime code implementing the uncommon trap logic.
 */
@NodeInfo
public final class PushInterpreterFrameNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<PushInterpreterFrameNode> TYPE = NodeClass.create(PushInterpreterFrameNode.class);
    @Input ValueNode framePc;
    @Input ValueNode frameSize;
    @Input ValueNode senderSp;
    @Input ValueNode initialInfo;

    public PushInterpreterFrameNode(ValueNode frameSize, ValueNode framePc, ValueNode senderSp, ValueNode initialInfo) {
        super(TYPE, StampFactory.forVoid());
        this.frameSize = frameSize;
        this.framePc = framePc;
        this.senderSp = senderSp;
        this.initialInfo = initialInfo;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value frameSizeValue = gen.operand(frameSize);
        Value framePcValue = gen.operand(framePc);
        Value senderSpValue = gen.operand(senderSp);
        Value initialInfoValue = gen.operand(initialInfo);
        ((HotSpotLIRGenerator) gen.getLIRGeneratorTool()).emitPushInterpreterFrame(frameSizeValue, framePcValue, senderSpValue, initialInfoValue);
    }

    @NodeIntrinsic
    public static native void pushInterpreterFrame(Word frameSize, Word framePc, Word senderSp, Word initialInfo);

}
