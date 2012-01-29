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
package com.oracle.max.graal.debug.internal;

import com.oracle.max.graal.debug.*;

public final class TimerImpl extends DebugValue implements DebugTimer {

    public static final TimerCloseable VOID_CLOSEABLE = new TimerCloseable() {
        @Override
        public void close() {
        }
    };

    public TimerImpl(String name) {
        super(name);
    }

    @Override
    public TimerCloseable start() {
        if (Debug.isTimeEnabled()) {
            final long startTime = System.currentTimeMillis();
            return new TimerCloseable() {
                @Override
                public void close() {
                    long timeSpan = System.currentTimeMillis() - startTime;
                    TimerImpl.this.addToCurrentValue(timeSpan);
                }
            };
        } else {
            return VOID_CLOSEABLE;
        }
    }
}
