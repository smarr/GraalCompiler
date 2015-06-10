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
package com.oracle.graal.hotspot.debug;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.nodes.debug.*;
import com.oracle.jvmci.common.*;
import com.oracle.jvmci.debug.*;
import com.oracle.jvmci.hotspot.*;
import com.oracle.jvmci.options.*;

import edu.umd.cs.findbugs.annotations.*;

//JaCoCo Exclude

/**
 * This class contains infrastructure to maintain counters based on {@link DynamicCounterNode}s. The
 * infrastructure is enabled by specifying either the GenericDynamicCounters or
 * BenchmarkDynamicCounters option.
 * <p>
 *
 * The counters are kept in a special area allocated for each native JavaThread object, and the
 * number of counters is configured using {@code -XX:GraalCounterSize=value}.
 * {@code -XX:+/-GraalCountersExcludeCompiler} configures whether to exclude compiler threads
 * (defaults to true).
 *
 * The subsystems that use the logging need to have their own options to turn on the counters, and
 * insert DynamicCounterNodes when they're enabled.
 *
 * Counters will be displayed as a rate (per second) if their group name starts with "~", otherwise
 * they will be displayed as a total number.
 *
 * <h1>Example</h1> In order to create statistics about allocations within the DaCapo pmd benchmark
 * the following steps are necessary:
 * <ul>
 * <li>Set {@code -XX:GraalCounterSize=value}. The actual required value depends on the granularity
 * of the profiling, 10000 should be enough for most cases.</li>
 * <li>Also: {@code -XX:+/-GraalCountersExcludeCompiler} specifies whether the numbers generated by
 * compiler threads should be excluded (default: true).</li>
 * <li>Start the DaCapo pmd benchmark with
 * {@code "-G:BenchmarkDynamicCounters=err, starting ====, PASSED in "} and
 * {@code -G:+ProfileAllocations}.</li>
 * <li>The numbers will only include allocation from compiled code!</li>
 * <li>The counters can be further configured by modifying the
 * {@link NewObjectSnippets#PROFILE_MODE} field.</li>
 * </ul>
 */
public class BenchmarkCounters {

    static class Options {

        //@formatter:off
        @Option(help = "Turn on the benchmark counters, and displays the results on VM shutdown", type = OptionType.Debug)
        private static final OptionValue<Boolean> GenericDynamicCounters = new OptionValue<>(false);
        @Option(help = "Turn on the benchmark counters, and displays the results every n milliseconds", type = OptionType.Debug)
        private static final OptionValue<Integer> TimedDynamicCounters = new OptionValue<>(-1);

        @Option(help = "Turn on the benchmark counters, and listen for specific patterns on System.out/System.err:%n" +
                       "Format: (err|out),start pattern,end pattern (~ matches multiple digits)%n" +
                       "Examples:%n" +
                       "  dacapo = 'err, starting =====, PASSED in'%n" +
                       "  specjvm2008 = 'out,Iteration ~ (~s) begins:,Iteration ~ (~s) ends:'", type = OptionType.Debug)
        private static final OptionValue<String> BenchmarkDynamicCounters = new OptionValue<>(null);
        @Option(help = "Use grouping separators for number printing", type = OptionType.Debug)
        private static final OptionValue<Boolean> DynamicCountersPrintGroupSeparator = new OptionValue<>(true);
        @Option(help = "Print in human readable format", type = OptionType.Debug)
        private static final OptionValue<Boolean> DynamicCountersHumanReadable = new OptionValue<>(true);
        //@formatter:on
    }

    private static final boolean DUMP_STATIC = false;

    public static boolean enabled = false;

    private static class Counter {
        public final int index;
        public final String group;
        public final AtomicLong staticCounters;

        public Counter(int index, String group, AtomicLong staticCounters) {
            this.index = index;
            this.group = group;
            this.staticCounters = staticCounters;
        }
    }

    public static final ConcurrentHashMap<String, Counter> counterMap = new ConcurrentHashMap<>();
    public static long[] delta;

    public static int getIndexConstantIncrement(String name, String group, HotSpotVMConfig config, long increment) {
        Counter counter = getCounter(name, group, config);
        counter.staticCounters.addAndGet(increment);
        return counter.index;
    }

    public static int getIndex(String name, String group, HotSpotVMConfig config) {
        Counter counter = getCounter(name, group, config);
        return counter.index;
    }

    @SuppressFBWarnings(value = "AT_OPERATION_SEQUENCE_ON_CONCURRENT_ABSTRACTION", justification = "concurrent abstraction calls are in synchronized block")
    private static Counter getCounter(String name, String group, HotSpotVMConfig config) throws JVMCIError {
        if (!enabled) {
            throw new JVMCIError("cannot access count index when counters are not enabled: " + group + ", " + name);
        }
        String nameGroup = name + "#" + group;
        Counter counter = counterMap.get(nameGroup);
        if (counter == null) {
            synchronized (BenchmarkCounters.class) {
                counter = counterMap.get(nameGroup);
                if (counter == null) {
                    counter = new Counter(counterMap.size(), group, new AtomicLong());
                    counterMap.put(nameGroup, counter);
                }
            }
        }
        assert counter.group.equals(group) : "mismatching groups: " + counter.group + " vs. " + group;
        int countersSize = config.jvmciCountersSize;
        if (counter.index >= countersSize) {
            throw new JVMCIError("too many counters, reduce number of counters or increase -XX:GraalCounterSize=... (current value: " + countersSize + ")");
        }
        return counter;
    }

    private static synchronized void dump(PrintStream out, double seconds, long[] counters, int maxRows) {
        if (!counterMap.isEmpty()) {
            out.println("====== dynamic counters (" + counterMap.size() + " in total) ======");
            TreeSet<String> set = new TreeSet<>();
            counterMap.forEach((nameGroup, counter) -> set.add(counter.group));
            for (String group : set) {
                if (group != null) {
                    if (DUMP_STATIC) {
                        dumpCounters(out, seconds, counters, true, group, maxRows);
                    }
                    dumpCounters(out, seconds, counters, false, group, maxRows);
                }
            }
            out.println("============================");

            clear(counters);
        }
    }

    private static synchronized void clear(long[] counters) {
        delta = counters;
    }

    private static synchronized void dumpCounters(PrintStream out, double seconds, long[] counters, boolean staticCounter, String group, int maxRows) {

        // collect the numbers
        long[] array;
        if (staticCounter) {
            array = new long[counterMap.size()];
            for (Counter counter : counterMap.values()) {
                array[counter.index] = counter.staticCounters.get();
            }
        } else {
            array = counters.clone();
            for (int i = 0; i < array.length; i++) {
                array[i] -= delta[i];
            }
        }
        Set<Entry<String, Counter>> counterEntrySet = counterMap.entrySet();
        if (Options.DynamicCountersHumanReadable.getValue()) {
            dumpHumanReadable(out, seconds, staticCounter, group, maxRows, array, counterEntrySet);
        } else {
            dumpComputerReadable(out, staticCounter, group, array, counterEntrySet);
        }
    }

    private static String getName(String nameGroup, String group) {
        return nameGroup.substring(0, nameGroup.length() - group.length() - 1);
    }

    private static void dumpHumanReadable(PrintStream out, double seconds, boolean staticCounter, String group, int maxRows, long[] array, Set<Entry<String, Counter>> counterEntrySet) {
        // sort the counters by putting them into a sorted map
        TreeMap<Long, String> sorted = new TreeMap<>();
        long sum = 0;
        for (Map.Entry<String, Counter> entry : counterEntrySet) {
            Counter counter = entry.getValue();
            int index = counter.index;
            if (counter.group.equals(group)) {
                sum += array[index];
                sorted.put(array[index] * array.length + index, getName(entry.getKey(), group));
            }
        }

        if (sum > 0) {
            long cutoff = sorted.size() < 10 ? 1 : Math.max(1, sum / 100);
            int cnt = sorted.size();

            // remove everything below cutoff and keep at most maxRows
            Iterator<Map.Entry<Long, String>> iter = sorted.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<Long, String> entry = iter.next();
                long counter = entry.getKey() / array.length;
                if (counter < cutoff || cnt > maxRows) {
                    iter.remove();
                }
                cnt--;
            }

            String numFmt = Options.DynamicCountersPrintGroupSeparator.getValue() ? "%,19d" : "%19d";
            if (staticCounter) {
                out.println("=========== " + group + " (static counters):");
                for (Map.Entry<Long, String> entry : sorted.entrySet()) {
                    long counter = entry.getKey() / array.length;
                    out.format(Locale.US, numFmt + " %3d%%  %s\n", counter, percentage(counter, sum), entry.getValue());
                }
                out.format(Locale.US, numFmt + " total\n", sum);
            } else {
                if (group.startsWith("~")) {
                    out.println("=========== " + group + " (dynamic counters), time = " + seconds + " s:");
                    for (Map.Entry<Long, String> entry : sorted.entrySet()) {
                        long counter = entry.getKey() / array.length;
                        out.format(Locale.US, numFmt + "/s %3d%%  %s\n", (long) (counter / seconds), percentage(counter, sum), entry.getValue());
                    }
                    out.format(Locale.US, numFmt + "/s total\n", (long) (sum / seconds));
                } else {
                    out.println("=========== " + group + " (dynamic counters):");
                    for (Map.Entry<Long, String> entry : sorted.entrySet()) {
                        long counter = entry.getKey() / array.length;
                        out.format(Locale.US, numFmt + " %3d%%  %s\n", counter, percentage(counter, sum), entry.getValue());
                    }
                    out.format(Locale.US, numFmt + " total\n", sum);
                }
            }
        }
    }

    private static void dumpComputerReadable(PrintStream out, boolean staticCounter, String group, long[] array, Set<Entry<String, Counter>> counterEntrySet) {
        String category = staticCounter ? "static counters" : "dynamic counters";
        for (Map.Entry<String, Counter> entry : counterEntrySet) {
            Counter counter = entry.getValue();
            if (counter.group.equals(group)) {
                String name = getName(entry.getKey(), group);
                int index = counter.index;
                long value = array[index];
                // TODO(je): escape strings
                out.printf("%s;%s;%s;%d\n", category, group, name, value);
            }
        }
    }

    private static long percentage(long counter, long sum) {
        return (counter * 200 + 1) / sum / 2;
    }

    private abstract static class CallbackOutputStream extends OutputStream {

        protected final PrintStream delegate;
        private final byte[][] patterns;
        private final int[] positions;

        public CallbackOutputStream(PrintStream delegate, String... patterns) {
            this.delegate = delegate;
            this.positions = new int[patterns.length];
            this.patterns = new byte[patterns.length][];
            for (int i = 0; i < patterns.length; i++) {
                this.patterns[i] = patterns[i].getBytes();
            }
        }

        protected abstract void patternFound(int index);

        @Override
        public void write(int b) throws IOException {
            try {
                delegate.write(b);
                for (int i = 0; i < patterns.length; i++) {
                    int j = positions[i];
                    byte[] cs = patterns[i];
                    byte patternChar = cs[j];
                    if (patternChar == '~' && Character.isDigit(b)) {
                        // nothing to do...
                    } else {
                        if (patternChar == '~') {
                            patternChar = cs[++positions[i]];
                        }
                        if (b == patternChar) {
                            positions[i]++;
                        } else {
                            positions[i] = 0;
                        }
                    }
                    if (positions[i] == patterns[i].length) {
                        positions[i] = 0;
                        patternFound(i);
                    }
                }
            } catch (RuntimeException e) {
                e.printStackTrace(delegate);
                throw e;
            }
        }
    }

    public static void initialize(final CompilerToVM compilerToVM) {
        final class BenchmarkCountersOutputStream extends CallbackOutputStream {

            private long startTime;
            private boolean running;
            private boolean waitingForEnd;

            private BenchmarkCountersOutputStream(PrintStream delegate, String start, String end) {
                super(delegate, new String[]{"\n", end, start});
            }

            @Override
            protected void patternFound(int index) {
                switch (index) {
                    case 2:
                        startTime = System.nanoTime();
                        BenchmarkCounters.clear(compilerToVM.collectCounters());
                        running = true;
                        break;
                    case 1:
                        if (running) {
                            waitingForEnd = true;
                        }
                        break;
                    case 0:
                        if (waitingForEnd) {
                            waitingForEnd = false;
                            running = false;
                            BenchmarkCounters.dump(delegate, (System.nanoTime() - startTime) / 1000000000d, compilerToVM.collectCounters(), 100);
                        }
                        break;
                }
            }
        }

        if (Options.BenchmarkDynamicCounters.getValue() != null) {
            String[] arguments = Options.BenchmarkDynamicCounters.getValue().split(",");
            if (arguments.length == 0 || (arguments.length % 3) != 0) {
                throw new JVMCIError("invalid arguments to BenchmarkDynamicCounters: (err|out),start,end,(err|out),start,end,... (~ matches multiple digits)");
            }
            for (int i = 0; i < arguments.length; i += 3) {
                if (arguments[i].equals("err")) {
                    System.setErr(new PrintStream(new BenchmarkCountersOutputStream(System.err, arguments[i + 1], arguments[i + 2])));
                } else if (arguments[i].equals("out")) {
                    System.setOut(new PrintStream(new BenchmarkCountersOutputStream(System.out, arguments[i + 1], arguments[i + 2])));
                } else {
                    throw new JVMCIError("invalid arguments to BenchmarkDynamicCounters: err|out");
                }
            }
            enabled = true;
        }
        if (Options.GenericDynamicCounters.getValue()) {
            enabled = true;
        }
        if (Options.TimedDynamicCounters.getValue() > 0) {
            Thread thread = new Thread() {
                long lastTime = System.nanoTime();
                PrintStream out = TTY.out;

                @Override
                public void run() {
                    while (true) {
                        try {
                            Thread.sleep(Options.TimedDynamicCounters.getValue());
                        } catch (InterruptedException e) {
                        }
                        long time = System.nanoTime();
                        dump(out, (time - lastTime) / 1000000000d, compilerToVM.collectCounters(), 10);
                        lastTime = time;
                    }
                }
            };
            thread.setDaemon(true);
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.start();
            enabled = true;
        }
        if (enabled) {
            clear(compilerToVM.collectCounters());
        }
    }

    public static void shutdown(CompilerToVM compilerToVM, long compilerStartTime) {
        if (Options.GenericDynamicCounters.getValue()) {
            dump(TTY.out, (System.nanoTime() - compilerStartTime) / 1000000000d, compilerToVM.collectCounters(), 100);
        }
    }
}
