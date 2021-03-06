/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.util;

import jdk.internal.jvmci.code.CodeCacheProvider;
import jdk.internal.jvmci.meta.ConstantReflectionProvider;
import jdk.internal.jvmci.meta.MetaAccessProvider;

import com.oracle.graal.compiler.common.spi.CodeGenProviders;
import com.oracle.graal.compiler.common.spi.ForeignCallsProvider;
import com.oracle.graal.nodes.spi.LoweringProvider;
import com.oracle.graal.nodes.spi.Replacements;
import com.oracle.graal.nodes.spi.StampProvider;
import com.oracle.graal.phases.tiers.PhaseContext;

/**
 * A set of providers, some of which may not be present (i.e., null).
 */
public class Providers implements CodeGenProviders {

    private final MetaAccessProvider metaAccess;
    private final CodeCacheProvider codeCache;
    private final LoweringProvider lowerer;
    private final ConstantReflectionProvider constantReflection;
    private final ForeignCallsProvider foreignCalls;
    private final Replacements replacements;
    private final StampProvider stampProvider;

    public Providers(MetaAccessProvider metaAccess, CodeCacheProvider codeCache, ConstantReflectionProvider constantReflection, ForeignCallsProvider foreignCalls, LoweringProvider lowerer,
                    Replacements replacements, StampProvider stampProvider) {
        this.metaAccess = metaAccess;
        this.codeCache = codeCache;
        this.constantReflection = constantReflection;
        this.foreignCalls = foreignCalls;
        this.lowerer = lowerer;
        this.replacements = replacements;
        this.stampProvider = stampProvider;
    }

    public Providers(Providers copyFrom) {
        this(copyFrom.getMetaAccess(), copyFrom.getCodeCache(), copyFrom.getConstantReflection(), copyFrom.getForeignCalls(), copyFrom.getLowerer(), copyFrom.getReplacements(),
                        copyFrom.getStampProvider());
    }

    public Providers(PhaseContext copyFrom) {
        this(copyFrom.getMetaAccess(), null, copyFrom.getConstantReflection(), null, copyFrom.getLowerer(), copyFrom.getReplacements(), copyFrom.getStampProvider());
    }

    public MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    public CodeCacheProvider getCodeCache() {
        return codeCache;
    }

    public ForeignCallsProvider getForeignCalls() {
        return foreignCalls;
    }

    public LoweringProvider getLowerer() {
        return lowerer;
    }

    public ConstantReflectionProvider getConstantReflection() {
        return constantReflection;
    }

    public Replacements getReplacements() {
        return replacements;
    }

    public StampProvider getStampProvider() {
        return stampProvider;
    }

    public Providers copyWith(MetaAccessProvider substitution) {
        return new Providers(substitution, codeCache, constantReflection, foreignCalls, lowerer, replacements, stampProvider);
    }

    public Providers copyWith(CodeCacheProvider substitution) {
        return new Providers(metaAccess, substitution, constantReflection, foreignCalls, lowerer, replacements, stampProvider);
    }

    public Providers copyWith(ConstantReflectionProvider substitution) {
        return new Providers(metaAccess, codeCache, substitution, foreignCalls, lowerer, replacements, stampProvider);
    }

    public Providers copyWith(ForeignCallsProvider substitution) {
        return new Providers(metaAccess, codeCache, constantReflection, substitution, lowerer, replacements, stampProvider);
    }

    public Providers copyWith(LoweringProvider substitution) {
        return new Providers(metaAccess, codeCache, constantReflection, foreignCalls, substitution, replacements, stampProvider);
    }

    public Providers copyWith(Replacements substitution) {
        return new Providers(metaAccess, codeCache, constantReflection, foreignCalls, lowerer, substitution, stampProvider);
    }

    public Providers copyWith(StampProvider substitution) {
        return new Providers(metaAccess, codeCache, constantReflection, foreignCalls, lowerer, replacements, substitution);
    }
}
