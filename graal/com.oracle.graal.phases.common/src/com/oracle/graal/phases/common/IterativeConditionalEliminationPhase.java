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
package com.oracle.graal.phases.common;

import com.oracle.graal.api.code.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.util.*;
import com.oracle.graal.phases.tiers.*;

public class IterativeConditionalEliminationPhase extends BasePhase<PhaseContext> {

    private static final int MAX_ITERATIONS = 256;

    private final CanonicalizerPhase canonicalizer;

    public IterativeConditionalEliminationPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    @Override
    protected void run(StructuredGraph graph, PhaseContext context) {
        ConditionalEliminationPhase eliminate = new ConditionalEliminationPhase(context.getRuntime());
        HashSetNodeChangeListener listener = new HashSetNodeChangeListener();
        int count = 0;
        while (true) {
            graph.trackInputChange(listener);
            graph.trackUsagesDroppedZero(listener);
            eliminate.apply(graph);
            graph.stopTrackingInputChange();
            graph.stopTrackingUsagesDroppedZero();
            if (listener.getChangedNodes().isEmpty()) {
                break;
            }
            for (Node node : graph.getNodes()) {
                if (node instanceof Simplifiable) {
                    listener.getChangedNodes().add(node);
                }
            }
            canonicalizer.applyIncremental(graph, context, listener.getChangedNodes());
            listener.getChangedNodes().clear();
            if (++count > MAX_ITERATIONS) {
                throw new BailoutException("Number of iterations in conditional elimination phase exceeds " + MAX_ITERATIONS);
            }
        }
    }
}
