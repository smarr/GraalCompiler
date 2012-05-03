/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.loop;

import java.util.*;
import java.util.Map.Entry;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.nodes.virtual.*;

public class SuperBlock {
    protected BeginNode entry;
    protected BeginNode exit;
    protected List<BeginNode> blocks;
    protected List<BeginNode> earlyExits;
    protected LoopBeginNode loop;
    protected Map<Node, Node> duplicationMapping;
    protected SuperBlock original;
    protected NodeBitMap loopNodes;

    public SuperBlock(BeginNode entry, BeginNode exit, List<BeginNode> blocks, List<BeginNode> earlyExits, LoopBeginNode loop) {
        this.entry = entry;
        this.exit = exit;
        this.blocks = blocks;
        this.earlyExits = earlyExits;
        this.loop = loop;
        assert blocks.contains(entry);
        assert !blocks.contains(exit) || exit == entry;
    }

    public Map<Node, Node> getDuplicationMapping() {
        return duplicationMapping;
    }

    public BeginNode getEntry() {
        return entry;
    }

    public NodeBitMap loopNodes() {
        if (loopNodes == null) {
            loopNodes = computeNodes();
        }
        return loopNodes;
    }

    public SuperBlock duplicate() {
        NodeBitMap nodes = loopNodes();
        Map<Node, Node> replacements = new HashMap<>();
        StructuredGraph graph = (StructuredGraph) entry.graph();
        BeginNode newEntry = graph.add(new BeginNode());
        BeginNode newExit = null;
        List<BeginNode> newEarlyExits = new ArrayList<>(earlyExits.size());
        if (!(exit instanceof MergeNode)) {
            newExit = graph.add(new BeginNode());
            replacements.put(exit, newExit);
        }
        replacements.put(entry, newEntry); // no merge/loop begin
        for (BeginNode earlyExit : earlyExits) {
            BeginNode newEarlyExit = graph.add(new BeginNode());
            newEarlyExits.add(newEarlyExit);
            replacements.put(earlyExit, newEarlyExit);
        }
        if (loop != null) {
            for (LoopEndNode end : loop.loopEnds()) {
                if (nodes.isMarked(end)) {
                    replacements.put(end, graph.add(new EndNode()));
                }
            }
        }
        Map<Node, Node> duplicates = graph.addDuplicates(nodes, replacements);
        if (exit instanceof MergeNode) {
            newExit = mergeExits(replacements, graph, duplicates, (MergeNode) exit);
        }

        List<BeginNode> newBlocks = new ArrayList<>(blocks.size());
        for (BeginNode block : blocks) {
            BeginNode newBlock = (BeginNode) duplicates.get(block);
            if (newBlock == null) {
                newBlock = (BeginNode) replacements.get(block);
            }
            assert newBlock != null : block;
            newBlocks.add(newBlock);
        }
        for (Entry<Node, Node> e : replacements.entrySet()) {
            duplicates.put(e.getKey(), e.getValue());
        }
        SuperBlock superBlock = new SuperBlock(newEntry, newExit, newBlocks, newEarlyExits, loop);
        superBlock.duplicationMapping = duplicates;
        superBlock.original = this;
        return superBlock;
    }

    private BeginNode mergeExits(Map<Node, Node> replacements, StructuredGraph graph, Map<Node, Node> duplicates, MergeNode mergeExit) {
        BeginNode newExit;
        List<EndNode> endsToMerge = new LinkedList<>();
        if (mergeExit == loop) {
            LoopBeginNode loopBegin = (LoopBeginNode) mergeExit;
            for (LoopEndNode le : loopBegin.loopEnds()) {
                Node duplicate = replacements.get(le);
                if (duplicate != null) {
                    endsToMerge.add((EndNode) duplicate);
                }
            }
        } else {
            for (EndNode end : mergeExit.forwardEnds()) {
                Node duplicate = duplicates.get(end);
                if (duplicate != null) {
                    endsToMerge.add((EndNode) duplicate);
                }
            }
        }

        if (endsToMerge.size() == 1) {
            EndNode end = endsToMerge.get(0);
            assert end.usages().count() == 0;
            newExit = graph.add(new BeginNode());
            end.replaceAtPredecessors(newExit);
            end.safeDelete();
        } else {
            assert endsToMerge.size() > 1;
            MergeNode newExitMerge = graph.add(new MergeNode());
            newExit = newExitMerge;
            FrameState state = mergeExit.stateAfter().duplicate();
            newExitMerge.setStateAfter(state); // this state is wrong (incudes phis from the loop begin) needs to be fixed while resolving data
            for (EndNode end : endsToMerge) {
                newExitMerge.addForwardEnd(end);
            }
        }
        return newExit;
    }

    public void finish() {
        if (original != null) {
            mergeEarlyExits((StructuredGraph) entry.graph(), original.earlyExits, duplicationMapping);
        }
    }

    private static void mergeEarlyExits(StructuredGraph graph, List<BeginNode> earlyExits, Map<Node, Node> duplicates) {
        for (BeginNode earlyExit : earlyExits) {
            BeginNode newEarlyExit = (BeginNode) duplicates.get(earlyExit);
            assert newEarlyExit != null;
            MergeNode merge = graph.add(new MergeNode());
            EndNode originalEnd = graph.add(new EndNode());
            EndNode newEnd = graph.add(new EndNode());
            merge.addForwardEnd(originalEnd);
            merge.addForwardEnd(newEnd);
            FixedNode next = earlyExit.next();
            earlyExit.setNext(originalEnd);
            newEarlyExit.setNext(newEnd);
            merge.setNext(next);
            FrameState exitState = earlyExit.stateAfter();
            FrameState state = exitState.duplicate();
            merge.setStateAfter(state);
            for (ValueProxyNode vpn : earlyExit.proxies().snapshot()) {
                ValueNode replaceWith;
                ValueProxyNode newVpn = (ValueProxyNode) duplicates.get(vpn);
                if (newVpn != null) {
                    PhiNode phi = graph.add(new PhiNode(vpn.kind(), merge, vpn.type()));
                    phi.addInput(vpn);
                    phi.addInput(newVpn);
                    if (vpn.type() == PhiType.Value) {
                        replaceWith = phi;
                    } else {
                        assert vpn.type() == PhiType.Virtual;
                        ValueNode vof = GraphUtil.unProxify(vpn);
                        ValueNode newVof = GraphUtil.unProxify(newVpn);
                        replaceWith = mergeVirtualChain(graph, vof, newVof, phi, earlyExit, newEarlyExit, merge);
                    }
                } else {
                    replaceWith = vpn.value();
                }
                state.replaceFirstInput(vpn, replaceWith);
                for (Node usage : vpn.usages().snapshot()) {
                    if (usage != exitState && !merge.isPhiAtMerge(usage)) {
                        usage.replaceFirstInput(vpn, replaceWith);
                    }
                }
            }
        }
    }

    private static ValueProxyNode findProxy(ValueNode value, BeginNode proxyPoint) {
        for (ValueProxyNode vpn : proxyPoint.proxies()) {
            ValueNode v = vpn;
            while (v instanceof ValueProxyNode) {
                v = ((ValueProxyNode) v).value();
                if (v == value) {
                    return vpn;
                }
            }
        }
        return null;
    }

    private static ValueNode mergeVirtualChain(
                    StructuredGraph graph,
                    ValueNode vof,
                    ValueNode newVof,
                    PhiNode vPhi,
                    BeginNode earlyExit,
                    BeginNode newEarlyExit,
                    MergeNode merge) {
        VirtualObjectNode vObject = virtualObject(vof);
        assert virtualObject(newVof) == vObject;
        ValueNode[] virtualState = virtualState(vof);
        ValueNode[] newVirtualState = virtualState(newVof);
        ValueNode chain = vPhi;
        for (int i = 0; i < virtualState.length; i++) {
            ValueNode value = virtualState[i];
            ValueNode newValue = newVirtualState[i];
            assert value.kind() == newValue.kind();
            if (value != newValue) {
                PhiNode valuePhi = graph.add(new PhiNode(value.kind(), merge, PhiType.Value));
                ValueProxyNode inputProxy = findProxy(value, earlyExit);
                if (inputProxy != null) {
                    ValueProxyNode newInputProxy = findProxy(newValue, newEarlyExit);
                    assert newInputProxy != null : "no proxy for " + newValue + " at " + newEarlyExit;
                    valuePhi.addInput(inputProxy);
                    valuePhi.addInput(newInputProxy);
                } else {
                    valuePhi.addInput(graph.unique(new ValueProxyNode(value, earlyExit, PhiType.Value)));
                    valuePhi.addInput(newValue);
                }
                chain = graph.add(new VirtualObjectFieldNode(vObject, chain, valuePhi, i));
            }
        }
        return chain;
    }

    public static ValueNode mergeVirtualChain(
                    StructuredGraph graph,
                    PhiNode vPhi,
                    MergeNode merge) {
        NodeInputList<ValueNode> virtuals = vPhi.values();
        VirtualObjectNode vObject = virtualObject(GraphUtil.unProxify(virtuals.first()));
        List<ValueNode[]> virtualStates = new ArrayList<>(virtuals.size());
        for (ValueNode virtual : virtuals) {
            virtualStates.add(virtualState(GraphUtil.unProxify(virtual)));
        }
        ValueNode chain = vPhi;
        int stateLength = virtualStates.get(0).length;
        for (int i = 0; i < stateLength; i++) {
            ValueNode v = null;
            boolean reconcile = false;
            for (ValueNode[] state : virtualStates) {
                if (v == null) {
                    v = state[i];
                } else if (v != state[i]) {
                    reconcile = true;
                    break;
                }
                assert v.kind() == state[i].kind();
            }
            if (reconcile) {
                PhiNode valuePhi = graph.add(new PhiNode(v.kind(), merge, PhiType.Value));
                for (ValueNode[] state : virtualStates) {
                    valuePhi.addInput(state[i]);
                }
                chain = graph.add(new VirtualObjectFieldNode(vObject, chain, valuePhi, i));
            }
        }
        return chain;
    }

    private static VirtualObjectNode virtualObject(ValueNode vof) {
        assert vof instanceof VirtualObjectFieldNode || (vof instanceof PhiNode && ((PhiNode) vof).type() == PhiType.Virtual) : vof;
        ValueNode currentField = vof;
        do {
            if (currentField instanceof VirtualObjectFieldNode) {
               return ((VirtualObjectFieldNode) currentField).object();
            } else {
                assert currentField instanceof PhiNode && ((PhiNode) currentField).type() == PhiType.Virtual : currentField;
                currentField = ((PhiNode) currentField).valueAt(0);
            }
        } while (currentField != null);
        throw new GraalInternalError("Invalid virtual chain : cound not find virtual object from %s", vof);
    }

    private static ValueNode[] virtualState(ValueNode vof) {
        VirtualObjectNode vObj = virtualObject(vof);
        int fieldsCount = vObj.fieldsCount();
        int dicovered = 0;
        ValueNode[] state = new ValueNode[fieldsCount];
        ValueNode currentField = vof;
        do {
            if (currentField instanceof VirtualObjectFieldNode) {
                int index = ((VirtualObjectFieldNode) currentField).index();
                if (state[index] == null) {
                    dicovered++;
                    state[index] = ((VirtualObjectFieldNode) currentField).input();
                    if (dicovered >= fieldsCount) {
                        break;
                    }
                }
                currentField = ((VirtualObjectFieldNode) currentField).lastState();
            } else {
                assert currentField instanceof PhiNode && ((PhiNode) currentField).type() == PhiType.Virtual : currentField;
                currentField = ((PhiNode) currentField).valueAt(0);
            }
        } while (currentField != null);
        return state;
    }

    private NodeBitMap computeNodes() {
        NodeBitMap nodes = entry.graph().createNodeBitMap();
        for (BeginNode b : blocks) {
            for (Node n : b.getBlockNodes()) {
                if (n instanceof Invoke) {
                    nodes.mark(((Invoke) n).callTarget());
                }
                if (n instanceof StateSplit) {
                    FrameState stateAfter = ((StateSplit) n).stateAfter();
                    if (stateAfter != null) {
                        nodes.mark(stateAfter);
                    }
                }
                nodes.mark(n);
            }
        }
        for (BeginNode earlyExit : earlyExits) {
            FrameState stateAfter = earlyExit.stateAfter();
            assert stateAfter != null;
            nodes.mark(stateAfter);
            nodes.mark(earlyExit);
            for (ValueProxyNode proxy : earlyExit.proxies()) {
                nodes.mark(proxy);
            }
        }

        for (BeginNode b : blocks) {
            for (Node n : b.getBlockNodes()) {
                for (Node usage : n.usages()) {
                    markFloating(usage, nodes, "");
                }
            }
        }

        if (entry instanceof LoopBeginNode) {
            for (PhiNode phi : ((LoopBeginNode) entry).phis()) {
                nodes.clear(phi);
            }
        }

        return nodes;
    }

    private static boolean markFloating(Node n, NodeBitMap loopNodes, String ind) {
        //System.out.println(ind + "markFloating(" + n + ")");
        if (loopNodes.isMarked(n)) {
            return true;
        }
        if (n instanceof FixedNode) {
            return false;
        }
        boolean mark = false;
        if (n instanceof PhiNode) {
            mark = loopNodes.isMarked(((PhiNode) n).merge());
            if (mark) {
                loopNodes.mark(n);
            } else {
                return false;
            }
        }
        for (Node usage : n.usages()) {
            if (markFloating(usage, loopNodes, " " + ind)) {
                mark = true;
            }
        }
        if (mark) {
            loopNodes.mark(n);
            return true;
        }
        return false;
    }

    public void insertBefore(FixedNode fixed) {
        assert entry.predecessor() == null;
        assert exit.next() == null;
        fixed.replaceAtPredecessors(entry);
        exit.setNext(fixed);
    }
}
