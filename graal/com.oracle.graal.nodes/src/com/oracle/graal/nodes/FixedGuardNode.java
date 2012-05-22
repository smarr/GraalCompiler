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
package com.oracle.graal.nodes;

import com.oracle.graal.cri.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.max.cri.ri.*;

public final class FixedGuardNode extends FixedWithNextNode implements Simplifiable, Lowerable, LIRLowerable, Node.IterableNodeType, Negatable {

    @Input private BooleanNode condition;
    private final RiDeoptReason deoptReason;
    private final RiDeoptAction action;
    private boolean negated;
    private final long leafGraphId;

    public BooleanNode condition() {
        return condition;
    }

    public void setCondition(BooleanNode x) {
        updateUsages(condition, x);
        condition = x;
    }

    public FixedGuardNode(BooleanNode condition, RiDeoptReason deoptReason, RiDeoptAction action, long leafGraphId) {
        this(condition, deoptReason, action, false, leafGraphId);
    }

    public FixedGuardNode(BooleanNode condition, RiDeoptReason deoptReason, RiDeoptAction action, boolean negated, long leafGraphId) {
        super(StampFactory.illegal());
        this.action = action;
        this.negated = negated;
        this.leafGraphId = leafGraphId;
        this.condition = condition;
        this.deoptReason = deoptReason;
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name && negated) {
            return "!" + super.toString(verbosity);
        } else {
            return super.toString(verbosity);
        }
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.emitGuardCheck(condition, deoptReason, action, negated, leafGraphId);
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (condition instanceof ConstantNode) {
            ConstantNode c = (ConstantNode) condition;
            if (c.asConstant().asBoolean() != negated) {
                ((StructuredGraph) graph()).removeFixed(this);
            } else {
                FixedNode next = this.next();
                if (next != null) {
                    tool.deleteBranch(next);
                }
                setNext(graph().add(new DeoptimizeNode(RiDeoptAction.InvalidateRecompile, deoptReason, leafGraphId)));
                return;
            }
        }
    }

    @Override
    public void lower(CiLoweringTool tool) {
        AnchorNode newAnchor = graph().add(new AnchorNode());
        newAnchor.dependencies().add(tool.createGuard(condition, deoptReason, action, negated, leafGraphId));
        ((StructuredGraph) graph()).replaceFixedWithFixed(this, newAnchor);
    }

    @Override
    public void negate() {
        negated = !negated;
    }
}
