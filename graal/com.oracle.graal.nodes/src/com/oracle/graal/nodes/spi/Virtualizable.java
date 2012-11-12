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
package com.oracle.graal.nodes.spi;

/**
 * This interface allows a node to convey information about what its effect would be if some of its inputs were
 * virtualized.
 */
public interface Virtualizable {

    /**
     * A node class can implement this method to convey information about what its effect would be if some of its inputs
     * were virtualized. All modifications must be made through the supplied tool, and not directly on the node, because
     * by the time this method is called the virtualized/non-virtualized state is still speculative and might not hold
     * because of loops, etc.
     *
     * @param tool the tool used to describe the effects of this node
     */
    void virtualize(VirtualizerTool tool);
}
