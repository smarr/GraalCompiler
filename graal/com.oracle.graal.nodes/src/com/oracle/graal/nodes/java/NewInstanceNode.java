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
package com.oracle.graal.nodes.java;

import java.lang.ref.Reference;
import java.util.Collections;

import jdk.internal.jvmci.meta.ResolvedJavaField;
import jdk.internal.jvmci.meta.ResolvedJavaType;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.VirtualizableAllocation;
import com.oracle.graal.nodes.spi.VirtualizerTool;
import com.oracle.graal.nodes.virtual.VirtualInstanceNode;

/**
 * The {@code NewInstanceNode} represents the allocation of an instance class object.
 */
@NodeInfo(nameTemplate = "New {p#instanceClass/s}")
public class NewInstanceNode extends AbstractNewObjectNode implements VirtualizableAllocation {

    public static final NodeClass<NewInstanceNode> TYPE = NodeClass.create(NewInstanceNode.class);
    protected final ResolvedJavaType instanceClass;

    public NewInstanceNode(ResolvedJavaType type, boolean fillContents) {
        this(TYPE, type, fillContents, null);
    }

    public NewInstanceNode(ResolvedJavaType type, boolean fillContents, FrameState stateBefore) {
        this(TYPE, type, fillContents, stateBefore);
    }

    protected NewInstanceNode(NodeClass<? extends NewInstanceNode> c, ResolvedJavaType type, boolean fillContents, FrameState stateBefore) {
        super(c, StampFactory.exactNonNull(type), fillContents, stateBefore);
        assert !type.isArray() && !type.isInterface() && !type.isPrimitive();
        this.instanceClass = type;
    }

    /**
     * Gets the instance class being allocated by this node.
     *
     * @return the instance class allocated
     */
    public ResolvedJavaType instanceClass() {
        return instanceClass;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        /*
         * Reference objects can escape into their ReferenceQueue at any safepoint, therefore
         * they're excluded from escape analysis.
         */
        if (!tool.getMetaAccessProvider().lookupJavaType(Reference.class).isAssignableFrom(instanceClass)) {
            VirtualInstanceNode virtualObject = createVirtualInstanceNode(true);
            ResolvedJavaField[] fields = virtualObject.getFields();
            ValueNode[] state = new ValueNode[fields.length];
            for (int i = 0; i < state.length; i++) {
                state[i] = defaultFieldValue(fields[i]);
            }
            tool.createVirtualObject(virtualObject, state, Collections.<MonitorIdNode> emptyList(), false);
            tool.replaceWithVirtual(virtualObject);
        }
    }

    protected VirtualInstanceNode createVirtualInstanceNode(boolean hasIdentity) {
        return new VirtualInstanceNode(instanceClass(), hasIdentity);
    }

    /* Factored out in a separate method so that subclasses can override it. */
    protected ConstantNode defaultFieldValue(ResolvedJavaField field) {
        return ConstantNode.defaultForKind(field.getType().getJavaKind(), graph());
    }
}
