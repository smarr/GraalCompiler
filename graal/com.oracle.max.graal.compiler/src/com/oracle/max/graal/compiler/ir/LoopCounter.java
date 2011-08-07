/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.ir;

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;


public final class LoopCounter extends FloatingNode {

    @Input    private Value init;

    @Input    private Value stride;

    @Input    private LoopBegin loopBegin;

    public Value init() {
        return init;
    }

    public void setInit(Value x) {
        updateUsages(init, x);
        init = x;
    }

    public Value stride() {
        return stride;
    }

    public void setStride(Value x) {
        updateUsages(stride, x);
        stride = x;
    }

    public LoopBegin loopBegin() {
        return loopBegin;
    }

    public void setLoopBegin(LoopBegin x) {
        updateUsages(loopBegin, x);
        loopBegin = x;
    }

    public LoopCounter(CiKind kind, Value init, Value stride, LoopBegin loop, Graph graph) {
        super(kind, graph);
        setInit(init);
        setStride(stride);
        setLoopBegin(loop);
    }

    @Override
    public void accept(ValueVisitor v) {
        // TODO Auto-generated method stub

    }

    @Override
    public void print(LogStream out) {
        out.print("loopcounter [").print(init()).print(",+").print(stride()).print("]");

    }

    @Override
    public Node copy(Graph into) {
        return new LoopCounter(kind, null, null, null, into);
    }

}
