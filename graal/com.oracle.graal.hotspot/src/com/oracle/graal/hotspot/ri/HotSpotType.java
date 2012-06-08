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
package com.oracle.graal.hotspot.ri;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;

/**
 * Common interface for all HotSpot RiType-implementations.
 */
public abstract class HotSpotType extends CompilerObject implements RiType {
    private static final long serialVersionUID = -4252886265301910771L;
    protected String name;

    @Override
    public final String name() {
        return name;
    }

    /**
     * Gets the object representing the C++ klassOop for this type.
     * Such a value cannot be safely exposed to Java code.
     */
    public abstract HotSpotKlassOop klassOop();
}
