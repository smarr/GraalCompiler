/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import jdk.internal.jvmci.hotspot.HotSpotVMConfig;
import jdk.internal.jvmci.meta.JavaKind;

import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.memory.address.AddressNode;
import com.oracle.graal.nodes.spi.LoweringProvider;

/**
 * HotSpot implementation of {@link LoweringProvider}.
 */
public interface HotSpotLoweringProvider extends LoweringProvider {

    void initialize(HotSpotProviders providers, HotSpotVMConfig config);

    int arrayScalingFactor(JavaKind kind);

    AddressNode createArrayAddress(StructuredGraph graph, ValueNode array, JavaKind elementKind, ValueNode index);

    Stamp loadStamp(Stamp stamp, JavaKind kind);

    ValueNode implicitLoadConvert(StructuredGraph graph, JavaKind kind, ValueNode value);

    ValueNode implicitStoreConvert(StructuredGraph graph, JavaKind kind, ValueNode value);
}
