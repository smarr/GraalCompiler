/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.word.*;

/**
 * Jumps to the exception handler specified by {@link #address}. This node is specific for the
 * {@link ExceptionHandlerStub} and should not be used elswhere.
 */
@NodeInfo
public final class JumpToExceptionHandlerNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<JumpToExceptionHandlerNode> TYPE = NodeClass.create(JumpToExceptionHandlerNode.class);
    @Input ValueNode address;

    public JumpToExceptionHandlerNode(ValueNode address) {
        super(TYPE, StampFactory.forVoid());
        this.address = address;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        ((HotSpotNodeLIRBuilder) gen).emitJumpToExceptionHandler(address);
    }

    @NodeIntrinsic
    public static native void jumpToExceptionHandler(Word address);
}
