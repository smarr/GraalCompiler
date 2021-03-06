/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot;

import static jdk.internal.jvmci.compiler.Compiler.ExitVMOnBailout;
import static jdk.internal.jvmci.compiler.Compiler.ExitVMOnException;
import static jdk.internal.jvmci.compiler.Compiler.PrintAfterCompilation;
import static jdk.internal.jvmci.compiler.Compiler.PrintBailout;
import static jdk.internal.jvmci.compiler.Compiler.PrintCompilation;
import static jdk.internal.jvmci.compiler.Compiler.PrintFilter;
import static jdk.internal.jvmci.compiler.Compiler.PrintStackTraceOnException;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import jdk.internal.jvmci.code.BailoutException;
import jdk.internal.jvmci.code.CompilationResult;
import jdk.internal.jvmci.code.InstalledCode;
import jdk.internal.jvmci.compiler.Compiler;
import jdk.internal.jvmci.hotspot.CompilerToVM;
import jdk.internal.jvmci.hotspot.HotSpotCodeCacheProvider;
import jdk.internal.jvmci.hotspot.HotSpotInstalledCode;
import jdk.internal.jvmci.hotspot.HotSpotJVMCIRuntimeProvider;
import jdk.internal.jvmci.hotspot.HotSpotNmethod;
import jdk.internal.jvmci.hotspot.HotSpotResolvedJavaMethod;
import jdk.internal.jvmci.hotspot.HotSpotVMConfig;
import jdk.internal.jvmci.hotspot.events.EmptyEventProvider;
import jdk.internal.jvmci.hotspot.events.EventProvider;
import jdk.internal.jvmci.hotspot.events.EventProvider.CompilationEvent;
import jdk.internal.jvmci.hotspot.events.EventProvider.CompilerFailureEvent;
import jdk.internal.jvmci.meta.ResolvedJavaMethod;
import jdk.internal.jvmci.service.Services;
import sun.misc.Unsafe;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.DebugCloseable;
import com.oracle.graal.debug.DebugDumpScope;
import com.oracle.graal.debug.DebugMetric;
import com.oracle.graal.debug.DebugTimer;
import com.oracle.graal.debug.Management;
import com.oracle.graal.debug.TTY;

//JaCoCo Exclude

public class CompilationTask {

    private static final Unsafe UNSAFE = initUnsafe();

    private static Unsafe initUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to get Unsafe", e);
            }
        }
    }

    private static final DebugMetric BAILOUTS = Debug.metric("Bailouts");

    private static final EventProvider eventProvider;

    static {
        EventProvider provider = Services.loadSingle(EventProvider.class, false);
        if (provider == null) {
            eventProvider = new EmptyEventProvider();
        } else {
            eventProvider = provider;
        }
    }

    private final HotSpotJVMCIRuntimeProvider jvmciRuntime;

    private final HotSpotGraalCompiler compiler;

    private final HotSpotResolvedJavaMethod method;
    private final int entryBCI;
    private final int id;

    /**
     * Specifies whether the compilation result is installed as the
     * {@linkplain HotSpotNmethod#isDefault() default} nmethod for the compiled method.
     */
    private final boolean installAsDefault;

    static class Lazy {
        /**
         * A {@link com.sun.management.ThreadMXBean} to be able to query some information about the
         * current compiler thread, e.g. total allocated bytes.
         */
        static final com.sun.management.ThreadMXBean threadMXBean = (com.sun.management.ThreadMXBean) Management.getThreadMXBean();
    }

    /**
     * The address of the JVMCIEnv associated with this compilation or 0L if no such object exists.
     */
    private final long jvmciEnv;

    public CompilationTask(HotSpotJVMCIRuntimeProvider jvmciRuntime, HotSpotGraalCompiler compiler, HotSpotResolvedJavaMethod method, int entryBCI, long jvmciEnv, int id, boolean installAsDefault) {
        this.jvmciRuntime = jvmciRuntime;
        this.compiler = compiler;
        this.method = method;
        this.entryBCI = entryBCI;
        this.id = id;
        this.jvmciEnv = jvmciEnv;
        this.installAsDefault = installAsDefault;
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    /**
     * Returns the compilation id of this task.
     *
     * @return compile id
     */
    public int getId() {
        return id;
    }

    public int getEntryBCI() {
        return entryBCI;
    }

    /**
     * Time spent in compilation.
     */
    private static final DebugTimer CompilationTime = Debug.timer("CompilationTime");

    /**
     * Meters the {@linkplain CompilationResult#getBytecodeSize() bytecodes} compiled.
     */
    private static final DebugMetric CompiledBytecodes = Debug.metric("CompiledBytecodes");

    public static final DebugTimer CodeInstallationTime = Debug.timer("CodeInstallation");

    @SuppressWarnings("try")
    public void runCompilation() {
        HotSpotVMConfig config = jvmciRuntime.getConfig();
        final long threadId = Thread.currentThread().getId();
        long startCompilationTime = System.nanoTime();
        HotSpotInstalledCode installedCode = null;
        final boolean isOSR = entryBCI != Compiler.INVOCATION_ENTRY_BCI;

        // Log a compilation event.
        CompilationEvent compilationEvent = eventProvider.newCompilationEvent();

        // If there is already compiled code for this method on our level we simply return.
        // JVMCI compiles are always at the highest compile level, even in non-tiered mode so we
        // only need to check for that value.
        if (method.hasCodeAtLevel(entryBCI, config.compilationLevelFullOptimization)) {
            return;
        }

        CompilationResult result = null;
        try (DebugCloseable a = CompilationTime.start()) {
            CompilationStatistics stats = CompilationStatistics.create(method, isOSR);
            final boolean printCompilation = PrintCompilation.getValue() && !TTY.isSuppressed();
            final boolean printAfterCompilation = PrintAfterCompilation.getValue() && !TTY.isSuppressed();
            if (printCompilation) {
                TTY.println(getMethodDescription() + "...");
            }

            TTY.Filter filter = new TTY.Filter(PrintFilter.getValue(), method);
            final long start;
            final long allocatedBytesBefore;
            if (printAfterCompilation || printCompilation) {
                start = System.currentTimeMillis();
                allocatedBytesBefore = printAfterCompilation || printCompilation ? Lazy.threadMXBean.getThreadAllocatedBytes(threadId) : 0L;
            } else {
                start = 0L;
                allocatedBytesBefore = 0L;
            }

            try (Scope s = Debug.scope("Compiling", new DebugDumpScope(String.valueOf(id), true))) {
                // Begin the compilation event.
                compilationEvent.begin();

                result = compiler.compile(method, entryBCI, mustRecordMethodInlining(config));

                result.setId(getId());
            } catch (Throwable e) {
                throw Debug.handle(e);
            } finally {
                // End the compilation event.
                compilationEvent.end();

                filter.remove();

                if (printAfterCompilation || printCompilation) {
                    final long stop = System.currentTimeMillis();
                    final int targetCodeSize = result != null ? result.getTargetCodeSize() : -1;
                    final long allocatedBytesAfter = Lazy.threadMXBean.getThreadAllocatedBytes(threadId);
                    final long allocatedBytes = (allocatedBytesAfter - allocatedBytesBefore) / 1024;

                    if (printAfterCompilation) {
                        TTY.println(getMethodDescription() + String.format(" | %4dms %5dB %5dkB", stop - start, targetCodeSize, allocatedBytes));
                    } else if (printCompilation) {
                        TTY.println(String.format("%-6d JVMCI %-70s %-45s %-50s | %4dms %5dB %5dkB", id, "", "", "", stop - start, targetCodeSize, allocatedBytes));
                    }
                }
            }

            try (DebugCloseable b = CodeInstallationTime.start()) {
                installedCode = (HotSpotInstalledCode) installMethod(result);
            }
            stats.finish(method, installedCode);
        } catch (BailoutException bailout) {
            BAILOUTS.increment();
            if (ExitVMOnBailout.getValue()) {
                TTY.out.println(method.format("Bailout in %H.%n(%p)"));
                bailout.printStackTrace(TTY.out);
                System.exit(-1);
            } else if (PrintBailout.getValue()) {
                TTY.out.println(method.format("Bailout in %H.%n(%p)"));
                bailout.printStackTrace(TTY.out);
            }
        } catch (Throwable t) {
            if (PrintStackTraceOnException.getValue() || ExitVMOnException.getValue()) {
                t.printStackTrace(TTY.out);
            }

            // Log a failure event.
            CompilerFailureEvent event = eventProvider.newCompilerFailureEvent();
            if (event.shouldWrite()) {
                event.setCompileId(getId());
                event.setMessage(t.getMessage());
                event.commit();
            }

            if (ExitVMOnException.getValue()) {
                System.exit(-1);
            }
        } finally {
            int compiledBytecodes = 0;
            int codeSize = 0;
            if (result != null) {
                compiledBytecodes = result.getBytecodeSize();
            }
            if (installedCode != null) {
                codeSize = installedCode.getSize();
            }
            CompiledBytecodes.add(compiledBytecodes);

            // Log a compilation event.
            if (compilationEvent.shouldWrite()) {
                compilationEvent.setMethod(method.format("%H.%n(%p)"));
                compilationEvent.setCompileId(getId());
                compilationEvent.setCompileLevel(config.compilationLevelFullOptimization);
                compilationEvent.setSucceeded(result != null && installedCode != null);
                compilationEvent.setIsOsr(isOSR);
                compilationEvent.setCodeSize(codeSize);
                compilationEvent.setInlinedBytes(compiledBytecodes);
                compilationEvent.commit();
            }

            if (jvmciEnv != 0) {
                long ctask = UNSAFE.getAddress(jvmciEnv + config.jvmciEnvTaskOffset);
                assert ctask != 0L;
                UNSAFE.putInt(ctask + config.compileTaskNumInlinedBytecodesOffset, compiledBytecodes);
            }
            long compilationTime = System.nanoTime() - startCompilationTime;
            if ((config.ciTime || config.ciTimeEach) && installedCode != null) {
                long timeUnitsPerSecond = TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS);
                CompilerToVM c2vm = jvmciRuntime.getCompilerToVM();
                c2vm.notifyCompilationStatistics(id, method, entryBCI != Compiler.INVOCATION_ENTRY_BCI, compiledBytecodes, compilationTime, timeUnitsPerSecond, installedCode);
            }
        }
    }

    /**
     * Determines whether to disable method inlining recording for the method being compiled.
     */
    private boolean mustRecordMethodInlining(HotSpotVMConfig config) {
        if (config.ciTime || config.ciTimeEach || CompiledBytecodes.isEnabled()) {
            return true;
        }
        if (jvmciEnv == 0 || UNSAFE.getByte(jvmciEnv + config.jvmciEnvJvmtiCanHotswapOrPostBreakpointOffset) != 0) {
            return true;
        }
        return false;
    }

    private String getMethodDescription() {
        return String.format("%-6d JVMCI %-70s %-45s %-50s %s", id, method.getDeclaringClass().getName(), method.getName(), method.getSignature().toMethodDescriptor(),
                        entryBCI == Compiler.INVOCATION_ENTRY_BCI ? "" : "(OSR@" + entryBCI + ") ");
    }

    @SuppressWarnings("try")
    private InstalledCode installMethod(final CompilationResult compResult) {
        final HotSpotCodeCacheProvider codeCache = (HotSpotCodeCacheProvider) jvmciRuntime.getHostJVMCIBackend().getCodeCache();
        InstalledCode installedCode = null;
        try (Scope s = Debug.scope("CodeInstall", new DebugDumpScope(String.valueOf(id), true), codeCache, method)) {
            installedCode = codeCache.installMethod(method, compResult, jvmciEnv, installAsDefault);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
        return installedCode;
    }

    @Override
    public String toString() {
        return "Compilation[id=" + id + ", " + method.format("%H.%n(%p)") + (entryBCI == Compiler.INVOCATION_ENTRY_BCI ? "" : "@" + entryBCI) + "]";
    }
}
