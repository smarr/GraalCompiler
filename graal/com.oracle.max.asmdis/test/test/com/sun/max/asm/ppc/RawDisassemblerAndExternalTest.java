/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package test.com.sun.max.asm.ppc;

import java.util.*;

import junit.framework.*;
import test.com.sun.max.asm.*;

import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.risc.ppc.*;

/**
 * JUnit harness for testing the generated PowerPC assembler against an external
 * assembler. The assembler is also tested by ensuring that its output
 * can be disassembled.
 */
public class RawDisassemblerAndExternalTest extends ExternalAssemblerTestCase {

    public RawDisassemblerAndExternalTest() {
        super();
    }

    public RawDisassemblerAndExternalTest(String name) {
        super(name);
    }

    public static Test suite() {
        final TestSuite suite = new TestSuite(RawDisassemblerAndExternalTest.class.getName());
        suite.addTestSuite(RawDisassemblerAndExternalTest.class);
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(RawDisassemblerAndExternalTest.class);
    }

    public void test_disassemblerAndExternalAssembler() {
        run(new PPC32AssemblyTester(EnumSet.of(AssemblyTestComponent.DISASSEMBLER, AssemblyTestComponent.EXTERNAL_ASSEMBLER)));
    }

}
