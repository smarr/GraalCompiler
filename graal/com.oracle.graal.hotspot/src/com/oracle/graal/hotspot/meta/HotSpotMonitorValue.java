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
package com.oracle.graal.hotspot.meta;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;

/**
 * Represents lock information in the debug information.
 */
public final class HotSpotMonitorValue extends Value {

    private static final long serialVersionUID = 8241681800464483691L;

    private Value owner;
    private final StackSlot slot;
    private final boolean eliminated;

    public HotSpotMonitorValue(Value owner, StackSlot slot, boolean eliminated) {
        super(Kind.Illegal);
        this.owner = owner;
        this.slot = slot;
        this.eliminated = eliminated;
    }

    public Value getOwner() {
        return owner;
    }

    public void setOwner(Value newOwner) {
        this.owner = newOwner;
    }

    public Value getSlot() {
        return slot;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    @Override
    public String toString() {
        return "monitor[" + owner + (slot != null ? ", " + slot : "") + (eliminated ? ", eliminated" : "") + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (eliminated ? 1231 : 1237);
        result = prime * result + ((owner == null) ? 0 : owner.hashCode());
        result = prime * result + ((slot == null) ? 0 : slot.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        HotSpotMonitorValue other = (HotSpotMonitorValue) obj;
        if (eliminated != other.eliminated) {
            return false;
        }
        if (owner == null) {
            if (other.owner != null) {
                return false;
            }
        } else if (!owner.equals(other.owner)) {
            return false;
        }
        if (slot == null) {
            if (other.slot != null) {
                return false;
            }
        } else if (!slot.equals(other.slot)) {
            return false;
        }
        return true;
    }
}
