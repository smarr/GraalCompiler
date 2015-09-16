/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.hotspot.test;

import static jdk.internal.jvmci.hotspot.HotSpotVMConfig.config;
import jdk.internal.jvmci.hotspot.HotSpotVMConfig;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.api.replacements.ClassSubstitution;
import com.oracle.graal.api.replacements.MethodSubstitution;
import com.oracle.graal.compiler.test.GraalCompilerTest;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.hotspot.nodes.CompressionNode;
import com.oracle.graal.hotspot.nodes.CompressionNode.CompressionOp;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

public class DataPatchTest extends GraalCompilerTest {

    public static double doubleSnippet() {
        return 84.72;
    }

    @Test
    public void doubleTest() {
        test("doubleSnippet");
    }

    public static Object oopSnippet() {
        return "asdf";
    }

    @Test
    public void oopTest() {
        test("oopSnippet");
    }

    private static Object compressUncompress(Object obj) {
        return obj;
    }

    public static Object narrowOopSnippet() {
        return compressUncompress("narrowAsdf");
    }

    @Test
    public void narrowOopTest() {
        Assume.assumeTrue("skipping narrow oop data patch test", config.useCompressedOops);
        test("narrowOopSnippet");
    }

    private static final HotSpotVMConfig config = config();
    private static boolean initReplacements = false;

    @Before
    public void initReplacements() {
        if (!initReplacements) {
            getReplacements().registerSubstitutions(DataPatchTest.class, DataPatchTestSubstitutions.class);
            initReplacements = true;
        }
    }

    @ClassSubstitution(DataPatchTest.class)
    private static class DataPatchTestSubstitutions {

        @MethodSubstitution
        public static Object compressUncompress(Object obj) {
            Object compressed = CompressionNode.compression(CompressionOp.Compress, obj, config.getOopEncoding());
            Object proxy = ConstantFoldBarrier.wrap(compressed);
            return CompressionNode.compression(CompressionOp.Uncompress, proxy, config.getOopEncoding());
        }
    }

    @NodeInfo
    private static final class ConstantFoldBarrier extends FloatingNode implements LIRLowerable {

        public static final NodeClass<ConstantFoldBarrier> TYPE = NodeClass.create(ConstantFoldBarrier.class);
        @Input protected ValueNode input;

        public ConstantFoldBarrier(ValueNode input) {
            super(TYPE, input.stamp());
            this.input = input;
        }

        public void generate(NodeLIRBuilderTool generator) {
            generator.setResult(this, generator.operand(input));
        }

        @NodeIntrinsic
        public static native Object wrap(Object object);
    }
}
