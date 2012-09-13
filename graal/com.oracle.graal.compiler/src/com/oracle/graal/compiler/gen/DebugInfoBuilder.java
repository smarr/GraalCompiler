/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.gen;

import java.util.*;
import java.util.Map.Entry;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.gen.LIRGenerator.LockScope;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.virtual.*;

public class DebugInfoBuilder {
    private final NodeMap<Value> nodeOperands;

    public DebugInfoBuilder(NodeMap<Value> nodeOperands) {
        this.nodeOperands = nodeOperands;
    }


    private HashMap<VirtualObjectNode, VirtualObject> virtualObjects = new HashMap<>();
    private IdentityHashMap<VirtualObjectNode, EscapeObjectState> objectStates = new IdentityHashMap<>();

    public LIRFrameState build(FrameState topState, LockScope locks, List<StackSlot> pointerSlots, LabelRef exceptionEdge, long leafGraphId) {
        assert virtualObjects.size() == 0;
        assert objectStates.size() == 0;

        // collect all VirtualObjectField instances:
        FrameState current = topState;
        do {
            for (EscapeObjectState state : current.virtualObjectMappings()) {
                if (objectStates == null) {
                    objectStates = new IdentityHashMap<>();
                }
                if (!objectStates.containsKey(state.object())) {
                    if (!(state instanceof MaterializedObjectState) || ((MaterializedObjectState) state).materializedValue() != state.object()) {
                        objectStates.put(state.object(), state);
                    }
                }
            }
            current = current.outerFrameState();
        } while (current != null);

        BytecodeFrame frame = computeFrameForState(topState, locks, leafGraphId);

        VirtualObject[] virtualObjectsArray = null;
        if (virtualObjects.size() != 0) {
            // fill in the VirtualObject values:
            // during this process new VirtualObjects might be discovered, so repeat until no more changes occur.
            boolean changed;
            do {
                changed = false;
                IdentityHashMap<VirtualObjectNode, VirtualObject> virtualObjectsCopy = new IdentityHashMap<>(virtualObjects);
                for (Entry<VirtualObjectNode, VirtualObject> entry : virtualObjectsCopy.entrySet()) {
                    if (entry.getValue().values() == null) {
                        VirtualObjectNode vobj = entry.getKey();
                        if (vobj instanceof BoxedVirtualObjectNode) {
                            BoxedVirtualObjectNode boxedVirtualObjectNode = (BoxedVirtualObjectNode) vobj;
                            entry.getValue().setValues(new Value[]{toValue(boxedVirtualObjectNode.getUnboxedValue())});
                        } else {
                            Value[] values = new Value[vobj.fields().length];
                            entry.getValue().setValues(values);
                            if (values.length > 0) {
                                changed = true;
                                VirtualObjectState currentField = (VirtualObjectState) objectStates.get(vobj);
                                assert currentField != null;
                                for (int i = 0; i < vobj.fields().length; i++) {
                                    values[i] = toValue(currentField.fieldValues().get(i));
                                }
                            }
                        }
                    }
                }
            } while (changed);

            virtualObjectsArray = virtualObjects.values().toArray(new VirtualObject[virtualObjects.size()]);
            virtualObjects.clear();
        }
        objectStates.clear();

        return new LIRFrameState(frame, virtualObjectsArray, pointerSlots, exceptionEdge);
    }

    private BytecodeFrame computeFrameForState(FrameState state, LockScope locks, long leafGraphId) {
        int numLocals = state.localsSize();
        int numStack = state.stackSize();
        int numLocks = (locks != null && locks.inliningIdentifier == state.inliningIdentifier()) ? locks.stateDepth + 1 : 0;

        Value[] values = new Value[numLocals + numStack + numLocks];
        for (int i = 0; i < numLocals; i++) {
            values[i] = toValue(state.localAt(i));
        }
        for (int i = 0; i < numStack; i++) {
            values[numLocals + i] = toValue(state.stackAt(i));
        }

        LockScope nextLock = locks;
        for (int i = numLocks - 1; i >= 0; i--) {
            assert locks != null && nextLock.inliningIdentifier == state.inliningIdentifier() && nextLock.stateDepth == i;

            Value owner = toValue(nextLock.object);
            StackSlot lockData = nextLock.lockData;
            boolean eliminated = nextLock.eliminated;
            values[numLocals + numStack + nextLock.stateDepth] = new MonitorValue(owner, lockData, eliminated);

            nextLock = nextLock.outer;
        }

        BytecodeFrame caller = null;
        if (state.outerFrameState() != null) {
            caller = computeFrameForState(state.outerFrameState(), nextLock, -1);
        } else {
            if (nextLock != null) {
                throw new BailoutException("unbalanced monitors: found monitor for unknown frame");
            }
        }
        assert state.bci >= 0 || state.bci == FrameState.BEFORE_BCI;
        BytecodeFrame frame = new BytecodeFrame(caller, state.method(), state.bci, state.rethrowException(), state.duringCall(), values, state.localsSize(), state.stackSize(), numLocks, leafGraphId);
        return frame;
    }

    private Value toValue(ValueNode value) {
        if (value instanceof VirtualObjectNode) {
            VirtualObjectNode obj = (VirtualObjectNode) value;
            EscapeObjectState state = objectStates.get(obj);
            if (state == null && obj.fields().length > 0) {
                // null states occur for objects with 0 fields
                throw new GraalInternalError("no mapping found for virtual object %s", obj);
            }
            if (state instanceof MaterializedObjectState) {
                return toValue(((MaterializedObjectState) state).materializedValue());
            } else {
                assert obj.fields().length == 0 || state instanceof VirtualObjectState;
                VirtualObject ciObj = virtualObjects.get(value);
                if (ciObj == null) {
                    ciObj = VirtualObject.get(obj.type(), null, virtualObjects.size());
                    virtualObjects.put(obj, ciObj);
                }
                Debug.metric("StateVirtualObjects").increment();
                return ciObj;
            }
        } else if (value instanceof ConstantNode) {
            Debug.metric("StateConstants").increment();
            return ((ConstantNode) value).value;

        } else if (value != null) {
            Debug.metric("StateVariables").increment();
            Value operand = nodeOperands.get(value);
            assert operand != null && (operand instanceof Variable || operand instanceof Constant) : operand + " for " + value;
            return operand;

        } else {
            // return a dummy value because real value not needed
            Debug.metric("StateIllegals").increment();
            return Value.IllegalValue;
        }
    }
}
