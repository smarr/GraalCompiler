/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.substitutions;

import com.oracle.graal.api.replacements.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.truffle.*;
import com.oracle.graal.truffle.nodes.asserts.*;
import com.oracle.graal.truffle.nodes.frame.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;

@ClassSubstitution(OptimizedCallTarget.class)
public class OptimizedCallTargetSubstitutions {

    @MacroSubstitution(macro = NeverInlineMacroNode.class, isStatic = false)
    public static native Object callHelper(OptimizedCallTarget target, PackedFrame caller, Arguments args);

    @MacroSubstitution(macro = NeverInlineMacroNode.class, isStatic = false)
    public static native Object interpreterCall(OptimizedCallTarget target, PackedFrame caller, Arguments args);

    @MacroSubstitution(macro = NeverInlineMacroNode.class, isStatic = false)
    public static native Object compiledCodeInvalidated(OptimizedCallTarget target, PackedFrame caller, Arguments args);

    @MethodSubstitution
    private static FrameWithoutBoxing createFrame(FrameDescriptor descriptor, PackedFrame caller, Arguments args) {
        return NewFrameNode.allocate(descriptor, caller, args);
    }
}
