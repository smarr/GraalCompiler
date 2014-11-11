/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.common.type;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.spi.*;

/**
 * Abstract base class of all pointer types.
 */
public abstract class AbstractPointerStamp extends Stamp {

    private final PointerType type;

    protected AbstractPointerStamp(PointerType type) {
        this.type = type;
    }

    public PointerType getType() {
        return type;
    }

    @Override
    public boolean isCompatible(Stamp otherStamp) {
        if (otherStamp instanceof AbstractPointerStamp) {
            AbstractPointerStamp other = (AbstractPointerStamp) otherStamp;
            return this.type == other.type;
        }
        return false;
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        return tool.getPointerKind(getType());
    }

    @Override
    public Kind getStackKind() {
        return Kind.Illegal;
    }

    @Override
    public Constant readConstant(ConstantReflectionProvider provider, Constant base, long displacement) {
        return provider.readPointerConstant(type, base, displacement);
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        throw GraalInternalError.shouldNotReachHere(type + " pointer has no Java type");
    }

    @Override
    public String toString() {
        return type + "*";
    }
}
