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
package com.oracle.graal.compiler;

import java.io.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.options.*;

public class GraalDebugConfig implements DebugConfig {

    // @formatter:off
    @Option(help = "Enable scope-based debugging", name = "Debug")
    public static final OptionValue<Boolean> DebugEnabled = new OptionValue<>(true);
    @Option(help = "Pattern for scope(s) to in which dumping is enabled (see DebugFilter and Debug.dump)")
    public static final OptionValue<String> Dump = new OptionValue<>(null);
    @Option(help = "Pattern for scope(s) to in which metering is enabled (see DebugFilter and Debug.metric)")
    public static final OptionValue<String> Meter = new OptionValue<>(null);
    @Option(help = "Pattern for scope(s) to in which timing is enabled (see DebugFilter and Debug.timer)")
    public static final OptionValue<String> Time = new OptionValue<>(null);
    @Option(help = "Pattern for scope(s) to in which logging is enabled (see DebugFilter and Debug.log)")
    public static final OptionValue<String> Log = new OptionValue<>(null);
    @Option(help = "Pattern for filtering debug scope output based on method context (see MethodFilter)")
    public static final OptionValue<String> MethodFilter = new OptionValue<>(null);
    @Option(help = "How to print metric and timing values:%n" +
                   "Name - aggregate by unqualified name%n" +
                   "Partial - aggregate by partially qualified name (e.g., A.B.C.D.Counter and X.Y.Z.D.Counter will be merged to D.Counter)%n" +
                   "Complete - aggregate by qualified name%n" +
                   "Thread - aggregate by qualified name and thread")
    public static final OptionValue<String> DebugValueSummary = new OptionValue<>("Complete");
    @Option(help = "Send Graal IR to dump handlers on error")
    public static final OptionValue<Boolean> DumpOnError = new OptionValue<>(false);
    @Option(help = "Enable expensive assertions")
    public static final OptionValue<Boolean> DetailedAsserts = new StableOptionValue<Boolean>() {
        @Override
        protected Boolean initialValue() {
            boolean enabled = false;
            // turn detailed assertions on when the general assertions are on (misusing the assert keyword for this)
            assert (enabled = true) == true;
            return enabled;
        }
    };
    /**
     * @see MethodFilter
     */
    @Option(help = "Pattern for method(s) to which intrinsification (if available) will be applied. " +
                   "By default, all available intrinsifications are applied except for methods matched " +
                   "by IntrinsificationsDisabled. See MethodFilter class for pattern syntax.")
    public static final OptionValue<String> IntrinsificationsEnabled = new OptionValue<>(null);
    /**
     * @see MethodFilter
     */
    @Option(help = "Pattern for method(s) to which intrinsification will not be applied. " +
                   "See MethodFilter class for pattern syntax.")
    public static final OptionValue<String> IntrinsificationsDisabled = new OptionValue<>("Object.clone");
    // @formatter:on

    private final DebugFilter logFilter;
    private final DebugFilter meterFilter;
    private final DebugFilter timerFilter;
    private final DebugFilter dumpFilter;
    private final MethodFilter[] methodFilter;
    private final List<DebugDumpHandler> dumpHandlers;
    private final PrintStream output;
    private final Set<Object> extraFilters = new HashSet<>();

    public GraalDebugConfig(String logFilter, String meterFilter, String timerFilter, String dumpFilter, String methodFilter, PrintStream output, List<DebugDumpHandler> dumpHandlers) {
        this.logFilter = DebugFilter.parse(logFilter);
        this.meterFilter = DebugFilter.parse(meterFilter);
        this.timerFilter = DebugFilter.parse(timerFilter);
        this.dumpFilter = DebugFilter.parse(dumpFilter);
        if (methodFilter == null || methodFilter.isEmpty()) {
            this.methodFilter = null;
        } else {
            this.methodFilter = com.oracle.graal.compiler.MethodFilter.parse(methodFilter);
        }

        // Report the filters that have been configured so the user can verify it's what they expect
        if (logFilter != null || meterFilter != null || timerFilter != null || dumpFilter != null || methodFilter != null) {
            // TTY.println(Thread.currentThread().getName() + ": " + toString());
        }
        this.dumpHandlers = dumpHandlers;
        this.output = output;
    }

    public boolean isLogEnabled() {
        return isEnabled(logFilter);
    }

    public boolean isMeterEnabled() {
        return isEnabled(meterFilter);
    }

    public boolean isDumpEnabled() {
        return isEnabled(dumpFilter);
    }

    public boolean isTimeEnabled() {
        return isEnabled(timerFilter);
    }

    public PrintStream output() {
        return output;
    }

    private boolean isEnabled(DebugFilter filter) {
        return checkDebugFilter(Debug.currentScope(), filter) && checkMethodFilter();
    }

    private static boolean checkDebugFilter(String currentScope, DebugFilter filter) {
        return filter != null && filter.matches(currentScope);
    }

    /**
     * Extracts a {@link JavaMethod} from an opaque debug context.
     * 
     * @return the {@link JavaMethod} represented by {@code context} or null
     */
    public static JavaMethod asJavaMethod(Object context) {
        if (context instanceof JavaMethod) {
            return (JavaMethod) context;
        }
        if (context instanceof StructuredGraph) {
            ResolvedJavaMethod method = ((StructuredGraph) context).method();
            if (method != null) {
                return method;
            }
        }
        return null;
    }

    private boolean checkMethodFilter() {
        if (methodFilter == null && extraFilters.isEmpty()) {
            return true;
        } else {
            for (Object o : Debug.context()) {
                if (extraFilters.contains(o)) {
                    return true;
                } else if (methodFilter != null) {
                    JavaMethod method = asJavaMethod(o);
                    if (method != null) {
                        if (com.oracle.graal.compiler.MethodFilter.matches(methodFilter, method)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Debug config:");
        add(sb, "Log", logFilter);
        add(sb, "Meter", meterFilter);
        add(sb, "Time", timerFilter);
        add(sb, "Dump", dumpFilter);
        add(sb, "MethodFilter", methodFilter);
        return sb.toString();
    }

    private static void add(StringBuilder sb, String name, Object filter) {
        if (filter != null) {
            sb.append(' ');
            sb.append(name);
            sb.append('=');
            if (filter instanceof Object[]) {
                sb.append(Arrays.toString((Object[]) filter));
            } else {
                sb.append(String.valueOf(filter));
            }
        }
    }

    @Override
    public RuntimeException interceptException(Throwable e) {
        if (e instanceof BailoutException) {
            return null;
        }
        Debug.setConfig(Debug.fixedConfig(true, true, false, false, dumpHandlers, output));
        Debug.log(String.format("Exception occurred in scope: %s", Debug.currentScope()));
        for (Object o : Debug.context()) {
            if (o instanceof Graph) {
                Debug.log("Context obj %s", o);
                if (DumpOnError.getValue()) {
                    Debug.dump(o, "Exception graph");
                } else {
                    Debug.log("Use -G:+DumpOnError to enable dumping of graphs on this error");
                }
            } else if (o instanceof Node) {
                String location = GraphUtil.approxSourceLocation((Node) o);
                if (location != null) {
                    Debug.log("Context obj %s (approx. location: %s)", o, location);
                } else {
                    Debug.log("Context obj %s", o);
                }
            } else {
                Debug.log("Context obj %s", o);
            }
        }
        return null;
    }

    @Override
    public Collection<DebugDumpHandler> dumpHandlers() {
        return dumpHandlers;
    }

    @Override
    public void addToContext(Object o) {
        extraFilters.add(o);
    }

    @Override
    public void removeFromContext(Object o) {
        extraFilters.remove(o);
    }
}
