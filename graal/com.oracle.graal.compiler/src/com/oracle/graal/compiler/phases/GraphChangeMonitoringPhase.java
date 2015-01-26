/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.phases;

import static com.oracle.graal.graph.Graph.NodeEvent.*;

import java.util.stream.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.Graph.NodeEventScope;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.util.*;
import com.oracle.graal.phases.tiers.*;

/**
 * A utility phase for detecting when a phase would change the graph and reporting extra information
 * about the effects. The phase is first run on a copy of the graph and if a change in that graph is
 * detected then it's rerun on the original graph inside a new debug scope under
 * GraphChangeMonitoringPhase. The message argument can be used to distinguish between the same
 * phase run at different points.
 *
 * @param <C>
 */
public class GraphChangeMonitoringPhase<C extends PhaseContext> extends PhaseSuite<C> {

    private final String message;

    public GraphChangeMonitoringPhase(String message, BasePhase<C> phase) {
        super();
        this.message = message;
        appendPhase(phase);
    }

    public GraphChangeMonitoringPhase(String message) {
        super();
        this.message = message;
    }

    @Override
    protected void run(StructuredGraph graph, C context) {
        HashSetNodeEventListener listener = new HashSetNodeEventListener().exclude(INPUT_CHANGED);
        try (NodeEventScope s = graph.trackNodeEvents(listener)) {
            StructuredGraph graphCopy = graph.copy();
            try (Scope s2 = Debug.sandbox("WithoutMonitoring", null)) {
                super.run(graphCopy, context);
            } catch (Throwable t) {
                Debug.handle(t);
            }
        }
        if (!listener.getNodes().isEmpty()) {
            // rerun it on the real graph in a new Debug scope so Dump and Log can find it.
            listener = new HashSetNodeEventListener().exclude(INPUT_CHANGED);
            try (NodeEventScope s = graph.trackNodeEvents(listener)) {
                try (Scope s2 = Debug.scope("GraphChangeMonitoring." + getName() + "-" + message)) {
                    if (Debug.isDumpEnabled(BasePhase.PHASE_DUMP_LEVEL)) {
                        Debug.dump(BasePhase.PHASE_DUMP_LEVEL, graph, "*** Before phase %s", getName());
                    }
                    super.run(graph, context);
                    if (Debug.isDumpEnabled(BasePhase.PHASE_DUMP_LEVEL)) {
                        Debug.dump(BasePhase.PHASE_DUMP_LEVEL, graph, "*** After phase %s", getName());
                    }
                }
                Debug.log("*** %s %s %s\n", message, graph, listener.getNodes().stream().filter(e -> !e.isAlive()).collect(Collectors.toSet()));
            }
        } else {
            // Go ahead and run it normally even though it should have no effect
            super.run(graph, context);
        }
    }
}
