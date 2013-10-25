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
package com.oracle.graal.truffle;

import java.util.concurrent.*;

import com.oracle.graal.compiler.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.printer.*;

public final class TruffleCompilerThread extends Thread {

    public static final ThreadFactory FACTORY = new ThreadFactory() {

        @Override
        public Thread newThread(Runnable r) {
            return new TruffleCompilerThread(r);
        }
    };

    private TruffleCompilerThread(Runnable r) {
        super(r);
        this.setName("TruffleCompilerThread-" + this.getId());
        this.setPriority(MAX_PRIORITY);
        this.setDaemon(true);
    }

    @Override
    public void run() {
        GraalDebugConfig debugConfig = null;
        if (Debug.isEnabled()) {
            debugConfig = DebugEnvironment.initialize(System.out);
        }
        try {
            super.run();
        } finally {
            if (debugConfig != null) {
                for (DebugDumpHandler dumpHandler : debugConfig.dumpHandlers()) {
                    try {
                        dumpHandler.close();
                    } catch (Throwable t) {

                    }
                }
            }
        }
    }
}
