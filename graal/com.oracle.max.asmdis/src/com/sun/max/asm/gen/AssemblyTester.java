/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm.gen;

import java.io.*;
import java.util.*;
import java.util.Arrays;
import java.util.concurrent.*;

import com.sun.max.*;
import com.sun.max.asm.*;
import com.sun.max.asm.dis.*;
import com.sun.max.collect.*;
import com.sun.max.io.*;
import com.sun.max.io.Streams.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.util.*;

/**
 * A test framework for assemblers and disassemblers.
 * <p>
 * For a given instruction set, you can run the assembler and the corresponding external GNU assembler (gas) with the
 * same set of arguments and compare the two generated byte arrays for equality. This requires running on a platform on
 * which the respective GNU assembler is installed or configuring SSH to enable remote execution on a machine with the
 * respective GNU assembler installed (see {@link #setRemoteUserAndHost} for more details).
 * <p>
 * You can also run the assembler and then apply the disassembler to its result and see if you end up with the same
 * method and arguments that you assembled.
 * <p>
 * Both these tests can be combined in one pass.
 * <p>
 * A test run iterates over every template in the assembly, and for each of those:
 * <ol>
 * <li>locates the assembler method in the raw assembler that corresponds to the current template</li>
 * <li>generates a sequence of test argument lists based on the current template,</li>
 * <li>performs the above described checks for each test argument list, invoking the located raw assembler method with
 * it</li>
 * </ol>
 * <p>
 * A sequence of test argument lists is constructed by building the cross product of eligible argument values. The
 * eligible values of symbolic arguments comprise all values of the respective argument type. The eligible values of
 * numeric arguments better just be a subset of the whole range defined by their argument type... They are a handful of
 * values representing all important boundary cases.
 * <p>
 * As with the original tester in the Klein assembly framework, this framework also features "negative" tests, i.e.
 * calls with illegal arguments that should be caught and reported. The number of negative tests is far fewer than in
 * the Klein assembly framework as the use of static typing in this framework leaves far fewer opportunities to specify
 * illegal arguments without incurring a syntax or type error from javac. The majority of the remaining possibilities
 * for specifying illegal arguments lies in RISC assemblers' featuring immediate fields whose ranges of legal values is
 * not precisely described by a Java primitive type (e.g. int, short, char, etc).
 */
public abstract class AssemblyTester<Template_Type extends Template> {

    private final Assembly<Template_Type> assembly;
    private final WordWidth addressWidth;
    private final EnumSet<AssemblyTestComponent> components;

    protected AssemblyTester(Assembly<Template_Type> assembly, WordWidth addressWidth, EnumSet<AssemblyTestComponent> components) {
        this.assembly = assembly;
        this.addressWidth = addressWidth;
        this.components = components;
        this.tmpFilePrefix = System.getProperty("user.name") + "-" + assembly.isa().name().toLowerCase() + "-asmTest-";
    }

    public Assembly<Template_Type> assembly() {
        return assembly;
    }

    public WordWidth addressWidth() {
        return addressWidth;
    }

    enum TestCaseLegality {
            LEGAL, ILLEGAL_BY_CONSTRAINT, ILLEGAL_BY_ARGUMENT
    }

    /**
     * This is an iterator over a (lazily generated) selection of test cases for a given template.
     * Using an iterator means that each test case is generated as needed and does not
     * use up memory longer than necessary. This helps prevent out of memory errors
     * for templates where the number of test cases can be very large.
     *
     * It is important to note that the value returned by {@link #next()} is only valid
     * until {@code next()} is called again. That is, the same {@code Sequence}
     * object is returned by each call to {@code next()}, only its contents have changed.
     *
     */
    class ArgumentListIterator implements Iterator<List<Argument>> {

        private final Template_Type template;
        private final Parameter[] parameters;
        private final Iterator<? extends Argument>[] testArgumentIterators;
        private final int count;
        private final Argument[] arguments;
        private final List<Argument> next;
        private final TestCaseLegality testCaseLegality;

        private boolean hasNext;
        private boolean advanced;
        private int iterations;

        /**
         * Creates an iterator over a set of test cases for a given template.
         *
         * @param template
         * @param legalCases if true, only legal test cases are returned by this iterator otherwise only illegal test
         *            cases are returned
         * @param legalArguments if {@code legalCases == true}, then this parameter is ignored. Otherwise, if true, then
         *            all the arguments in a returned test case are {@link Parameter#getLegalTestArguments legal}
         *            otherwise at least one argument in a returned test case will be
         *            {@link Parameter#getIllegalTestArguments illegal}
         */
        ArgumentListIterator(Template_Type template, TestCaseLegality testCaseLegality) {
            this.testCaseLegality  = testCaseLegality;
            this.template = template;
            this.parameters = template.parameters().toArray(new Parameter[template.parameters().size()]);
            this.count = template.parameters().size();
            this.arguments = new Argument[count];
            this.next = Arrays.asList(arguments);
            final Class<Iterator<? extends Argument>[]> type = null;
            this.testArgumentIterators = Utils.cast(type, new Iterator[count]);
            this.hasNext = advance();
        }

        /**
         * @return the number of times {@link #next} has been invoked on this iterator without throwing a NoSuchElementException.
         */
        public int iterations() {
            return iterations;
        }

        public boolean hasNext() {
            if (count == 0) {
                return testCaseLegality == TestCaseLegality.LEGAL ? (iterations == 0) : false;
            }
            if (!advanced) {
                hasNext = advance();
            }
            return hasNext;
        }

        /**
         * The returned sequence is only valid for a single iteration and so should be copied
         * if needed after this iteration.
         */
        public List<Argument> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            advanced = false;
            ++iterations;
            return next;
        }

        /**
         * Advances this iterator to the next argument list.
         *
         * @return true if the iterator could advance
         */
        private boolean advance() {
            assert !advanced;
            advanced = true;
            if (count == 0) {
                return testCaseLegality == TestCaseLegality.LEGAL;
            }
            boolean result;
            do {
                result = advance0();
            } while (result && isLegalArgumentList(template, next) != (testCaseLegality == TestCaseLegality.LEGAL));
            return result;
        }

        /**
         * Advances the test argument iterator for parameter {@code i}.
         *
         * @return true if the iterator could advance
         */
        private boolean advanceArgumentFor(int i) {
            if (testArgumentIterators[i].hasNext()) {
                arguments[i] = testArgumentIterators[i].next();
                return true;
            }
            return false;
        }

        /**
         * Advances this iterator to the next (potentially invalid) argument list.
         *
         * @return true if the iterator could advance
         */
        private boolean advance0() {
            for (int i = count - 1; i >= 0; --i) {
                if (testArgumentIterators[i] != null) {
                    if (advanceArgumentFor(i)) {
                        return true;
                    }
                    if (i == 0) {
                        return false;
                    }
                }

                // Reset iterator over test arguments of the i'th parameter
                final Parameter parameter = parameters[i];
                if (testCaseLegality != TestCaseLegality.ILLEGAL_BY_ARGUMENT) {
                    final Iterable<? extends Argument> argumentsIterable = parameter.getLegalTestArguments();
                    final ArgumentRange argumentRange = parameter.argumentRange();
                    if (argumentRange == null || !argumentRange.appliesInternally()) {
                        testArgumentIterators[i] = argumentsIterable.iterator();
                    } else {
                        testArgumentIterators[i] = new FilterIterator<>(argumentsIterable.iterator(), new Predicate<Argument>() {
                            public boolean evaluate(Argument argument) {
                                return argumentRange.includes(argument);
                            }
                        });
                    }
                } else {
                    Iterator<? extends Argument> iterator = parameter.getIllegalTestArguments().iterator();
                    if (!iterator.hasNext()) {
                        // For parameters that have no illegal values (e.g. a symbolic register parameter)
                        // at least one argument must be returned otherwise illegal test cases where only
                        // one argument is illegal will never be returned by this iterator.
                        //
                        // Instead of iterating over all the legal arguments for such a parameter, only
                        // the last legal value is used. This involves retrieving the iterator twice but
                        // that's much cheaper than many more redundant iterations.
                        iterator = parameter.getLegalTestArguments().iterator();
                        int n = 0;
                        while (iterator.hasNext()) {
                            ++n;
                            iterator.next();
                        }
                        iterator = parameter.getLegalTestArguments().iterator();
                        while (n-- > 1) {
                            iterator.next();
                        }
                        assert iterator.hasNext();
                        testArgumentIterators[i] = iterator;
                    } else {
                        testArgumentIterators[i] = iterator;
                    }
                }
                if (!advanceArgumentFor(i)) {
                    return false;
                }
            }
            return true;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Determines if a given set of arguments for a given template is legal.
     */
    protected abstract boolean isLegalArgumentList(Template_Type template, List<Argument> arguments);

    protected abstract void assembleExternally(IndentWriter stream, Template_Type template, List<Argument> argumentList, String label);

    private final String tmpFilePrefix;
    private static final String SOURCE_EXTENSION = ".s";
    private static final String BINARY_EXTENSION = ".o";

    private static boolean findExcludedDisassemblerTestArgument(List<? extends Parameter> parameters, List<Argument> arguments) {
        for (int i = 0; i < parameters.size(); i++) {
            if (parameters.get(i).excludedDisassemblerTestArguments().contains(arguments.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean findExcludedExternalTestArgument(List<? extends Parameter> parameters, List<Argument> arguments) {
        for (int i = 0; i < parameters.size(); i++) {
            final Parameter parameter = parameters.get(i);
            if (parameter.excludedExternalTestArguments().contains(arguments.get(i))) {
                return true;
            }
            final ArgumentRange argumentRange = parameters.get(i).argumentRange();
            if (argumentRange != null && !argumentRange.includes(arguments.get(i))) {
                return true;
            }
        }
        return false;
    }

    protected final int nNOPs = 10;

    private File createExternalSourceFile(Template_Type template, Iterator<List<Argument>> argumentLists) throws IOException {
        final File sourceFile = File.createTempFile(tmpFilePrefix + template.internalName(), SOURCE_EXTENSION);
        final IndentWriter stream = new IndentWriter(new PrintWriter(new BufferedWriter(new FileWriter(sourceFile))));
        stream.indent();
        for (int i = 0; i < nNOPs; i++) {
            stream.println("nop");
        }
        createExternalSource(template, argumentLists, stream);
        for (int i = 0; i < nNOPs; i++) {
            stream.println("nop");
        }
        stream.outdent();
        stream.close();
        return sourceFile;
    }

    private void createExternalSource(Template_Type template, Iterator<List<Argument>> argumentLists, IndentWriter stream) {
        int i = 0;
        while (argumentLists.hasNext()) {
            final List<Argument> argumentList = argumentLists.next();
            if (!findExcludedExternalTestArgument(template.parameters(), argumentList)) {
                final String label = "label" + i;
                assembleExternally(stream, template, argumentList, label);
            }
            ++i;
        }
    }

    protected abstract String assemblerCommand();

    private String remoteAssemblerPath = "";

    /**
     * Sets the path of the directory on a remote machine containing the
     * {@link #assemblerCommand assembler command}. This is required as
     * some SSH installations only provide a very minimal environment
     * for remotely executed commands.
     *
     * @param path the absolute path to the directory containing the assembler executable (must not be null)
     */
    public void setRemoteAssemblerPath(String path) {
        assert path != null;
        remoteAssemblerPath = path;
    }

    private String remoteUserAndHost;

    /**
     * Sets the {@code user@host} string that will be used to execute the external
     * assembler on a remote host via SSH. If this value is not set or set to null, the
     * external assembler will be executed on the local machine.
     * <p>
     * Execution on a remote machine is performed via the use of the 'ssh' and 'scp'
     * native executables. As such, these executables must be on the user's path.
     * <p>
     * The SSH layer authenticates with the remote machine via public key authentication.
     * Configuring the local and remote machines for this authentication is described in
     * the ssh man page.
     * <p>
     * Here's the console output
     * captured while configuring user {@code dsimon} on machine {@code local}
     * to be able to remotely execute via SSH as user {@code dsimon} on machine
     * {@code remote}:
     * <p>
     * <pre>
     * [dsimon@local:~]$ <b>ssh-keygen -t dsa</b>
     * Generating public/private dsa key pair.
     * Enter file in which to save the key (/home/dsimon/.ssh/id_dsa):
     * Enter passphrase (empty for no passphrase):
     * Enter same passphrase again:
     * Your identification has been saved in /home/dsimon/.ssh/id_dsa.
     * Your public key has been saved in /home/dsimon/.ssh/id_dsa.pub.
     * The key fingerprint is:
     * 88:ec:17:53:7a:e1:35:bd:cf:cc:0a:bb:dd:ec:99:8d dsimon@local
     * [dsimon@local:~]$ <b>scp /home/dsimon/.ssh/id_dsa.pub dsimon@remote:</b>
     * Password:
     * id_dsa.pub                                                                    100%  617     0.6KB/s   00:00
     * [dsimon@local:~]$ ssh dsimon@remote
     * Password:
     * [dsimon@remote:~]$ <b>{@literal cat id_dsa.pub >> .ssh/authorized_keys2}</b>
     * </pre>
     *
     * @param remoteUserAndHost a {@code user@host} value denoting a machine that
     *        supports remote execution via SSH2 using public key authentication
     */
    public void setRemoteUserAndHost(String remoteUserAndHost) {
        this.remoteUserAndHost = remoteUserAndHost;
    }

    /**
     * Executes a command in a subprocess redirecting the standard streams of the
     * subprocess to/from the standard streams of the current process.
     *
     * @param command  the command line to execute
     */
    private static void exec(String command) throws IOException, InterruptedException {
        exec(command, System.out, System.err, System.in);
    }

    /**
     * Executes a command in a subprocess redirecting the standard streams of the
     * subprocess to/from the supplied streams.
     *
     * @param command  the command line to execute
     * @param out   the stream to which standard output will be directed
     * @param err   the stream to which standard error output will be directed
     * @param in    the stream from which standard input will be read
     */
    private static void exec(String command, OutputStream out, OutputStream err, InputStream in) throws IOException, InterruptedException {
        final Process process = Runtime.getRuntime().exec(command);
        try {
            final Redirector stderr = Streams.redirect(process, process.getErrorStream(), err, command + " [stderr]", 50);
            final Redirector stdout = Streams.redirect(process, process.getInputStream(), out, command + " [stdout]");
            final Redirector stdin = Streams.redirect(process, in, process.getOutputStream(), command + " [stdin]");
            final int exitValue = process.waitFor();
            stderr.close();
            stdout.close();
            stdin.close();
            if (exitValue != 0) {
                throw ProgramError.unexpected("execution of command failed: " + command + " [exit code = " + exitValue + "]");
            }
        } finally {
            process.destroy();
        }
    }

    private File createExternalBinaryFile(File sourceFile) throws IOException {
        try {
            final File binaryFile = new File(sourceFile.getPath().substring(0, sourceFile.getPath().length() - SOURCE_EXTENSION.length()) + BINARY_EXTENSION);
            if (remoteUserAndHost != null) {

                // Copy input source to remote machine
                exec("scp -C " + sourceFile.getAbsolutePath() + " " + remoteUserAndHost + ":" + sourceFile.getName());

                // Execute assembler remotely
                exec("ssh " + remoteUserAndHost + " " + remoteAssemblerPath + assemblerCommand() + " -o " + binaryFile.getName() + " " + sourceFile.getName());

                // Copy output binary to local machine
                exec("scp -C " + remoteUserAndHost + ":" + binaryFile.getName() + " " + binaryFile.getAbsolutePath());

                // Delete input source and output binary from remote machine
                exec("ssh " + remoteUserAndHost + " rm " + binaryFile.getName() + " " + sourceFile.getName());
            } else {
                exec(assemblerCommand() + " " + sourceFile.getAbsolutePath() + " -o " + binaryFile.getAbsolutePath());
            }
            return binaryFile;
        } catch (InterruptedException e) {
            throw new InterruptedIOException(e.toString());
        }
    }

    protected abstract boolean readNop(InputStream stream) throws IOException;

    private boolean findStart(InputStream stream) throws IOException {
        while (stream.available() > 0) {
            if (readNop(stream)) {
                boolean found = true;
                for (int i = 1; i < nNOPs; i++) {
                    if (!readNop(stream)) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets a string representation of the value and bitrange of each field in an assembled instruction.
     * For example, the returned value for an assembled Power PC {@code rldimi} instruction may be
     * {@code "opcd[0:5]=31{011111} fxm[12:19]=1{00000001} rs[6:10]=1{00001} bit_11[11:11]=-1{1} res_20[20:20]=0{0} xo_21_30[21:30]=144{0010010000} res_31[31:31]=0{0}"}.
     */
    protected abstract String disassembleFields(Template_Type template, byte[] assembledInstruction);

    protected abstract byte[] readExternalInstruction(PushbackInputStream stream, Template_Type template, byte[] internalBytes) throws IOException;

    protected abstract Assembler createTestAssembler();

    protected abstract Disassembler createTestDisassembler();

    /**
     * We use this more complicated comparison instead of 'Sequence.equals()',
     * because some arguments with different identity may have equal values,
     * e.g. 'FPStackRegister.ST.value() == FPStackRegister.ST_0.value()'.
     * It would have been much more clean to override 'equals()' of those argument classes,
     * but they are enums and Java predeclares methods inherited via Enum final :-(
     */
    private static boolean equals(List<Argument> arguments1, List<Argument> arguments2) {
        if (arguments1.size() != arguments2.size()) {
            return false;
        }
        for (int i = 0; i < arguments1.size(); i++) {
            final Argument argument1 = arguments1.get(i);
            final Argument argument2 = arguments2.get(i);
            if (!argument1.equals(argument2)) {
                if (!Classes.areRelated(argument1.getClass(), argument2.getClass()) || argument1.asLong() != argument2.asLong()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void createExternalSource(Template_Type template, IndentWriter stream) {
        if (template.isExternallyTestable()) {
            final ArgumentListIterator argumentLists = new ArgumentListIterator(template, TestCaseLegality.LEGAL);
            createExternalSource(template, argumentLists, stream);
            Trace.line(2, "template: " + template + "  [" + argumentLists.iterations() + " test cases]");
        }
    }

    private void testDisassembler(Template_Type template, List<Argument> argumentList, byte[] internalResult) throws IOException, AssemblyException {
        final BufferedInputStream disassemblyStream = new BufferedInputStream(new ByteArrayInputStream(internalResult));
        final Disassembler disassembler = createTestDisassembler();
        disassembler.setAbstractionPreference(template.instructionDescription().isSynthetic() ? Disassembler.AbstractionPreference.SYNTHETIC : Disassembler.AbstractionPreference.RAW);
        disassembler.setExpectedNumberOfArguments(argumentList.size());
        final List<DisassembledObject> disassembledObjects = disassembler.scanOne(disassemblyStream);

        boolean matchFound = false;
        for (DisassembledObject disassembledObject : disassembledObjects) {
            if (disassembledObject instanceof DisassembledInstruction) {
                final DisassembledInstruction disassembledInstruction = (DisassembledInstruction) disassembledObject;
                matchFound = matchFound ||
                             (disassembledInstruction.template().isEquivalentTo(template) &&
                                equals(disassembledInstruction.arguments(), argumentList) &&
                                Arrays.equals(disassembledInstruction.bytes(), internalResult));
            }
        }

        final int available = disassemblyStream.available();
        if (available != 0 || !matchFound) {
            System.err.println("internal disassembler test failed - " + disassembledObjects.size() + " false matches found: ");
//final Assembler assembler = createTestAssembler();
//assembly().assemble(assembler, template, argumentList);
//disassemblyStream.reset();
//disassembler.scanOne(disassemblyStream);
            if (available != 0) {
                System.err.print("extra bytes at end of disassembly stream:");
                final int bytesToPrint = Math.min(available, 200);
                for (int i = 0; i < bytesToPrint; ++i) {
                    final int b = disassemblyStream.read();
                    System.err.print(" 0x" + Integer.toHexString(b));
                }
                if (bytesToPrint < available) {
                    System.err.print("... [" + (available - bytesToPrint) + " more]");
                }
                System.err.println();
            }
            int matchNumber = 1;
            for (DisassembledObject disassembledObject : disassembledObjects) {
                if (disassembledObject instanceof DisassembledInstruction) {
                    final DisassembledInstruction disassembledInstruction = (DisassembledInstruction) disassembledObject;
                    System.err.println();
                    System.err.println("False match number " + matchNumber + ":");
                    System.err.println("    assembled template: " + template);
                    System.err.println(" disassembled template: " + disassembledInstruction.template());
                    System.err.println("   assembled arguments: " + argumentList);
                    System.err.println("disassembled arguments: " + disassembledInstruction.arguments());
                    System.err.println("       assembled bytes: " + DisassembledInstruction.toHexString(internalResult));
                    System.err.println("    disassembled bytes: " + DisassembledInstruction.toHexString(disassembledInstruction.bytes()));
                    ++matchNumber;
                }
            }
            throw ProgramError.unexpected("mismatch between internal assembler and disassembler");
        }
        disassemblyStream.close();
    }

    private void testTemplate(final Template_Type template, List<File> temporaryFiles) throws IOException, AssemblyException {
        final boolean testingExternally = components.contains(AssemblyTestComponent.EXTERNAL_ASSEMBLER) && template.isExternallyTestable();

        // Process legal test cases
        final ArgumentListIterator argumentLists = new ArgumentListIterator(template, TestCaseLegality.LEGAL);
        ProgramError.check(argumentLists.hasNext(), "no test cases were generated for template: " + template);
        final File binaryFile;
        final PushbackInputStream externalInputStream;
        if (testingExternally) {
            final File sourceFile =  createExternalSourceFile(template, argumentLists);
            temporaryFiles.add(sourceFile);
            binaryFile =  createExternalBinaryFile(sourceFile);
            temporaryFiles.add(binaryFile);
            externalInputStream = new PushbackInputStream(new BufferedInputStream(new FileInputStream(binaryFile)));
            if (!findStart(externalInputStream)) {
                throw ProgramError.unexpected("could not find start sequence in: " + binaryFile.getAbsolutePath());
            }
        } else {
            binaryFile = null;
            externalInputStream = null;
        }

        int testCaseNumber = 0;

        for (final ArgumentListIterator iterator = new ArgumentListIterator(template, TestCaseLegality.LEGAL); iterator.hasNext();) {
            final List<Argument> argumentList = iterator.next();
            final Assembler assembler = createTestAssembler();
            assembly().assemble(assembler, template, argumentList);
            final byte[] internalResult = assembler.toByteArray();
            if (Trace.hasLevel(3)) {
                Trace.line(3, "assembleInternally[" + testCaseNumber + "]: " + Assembly.createMethodCallString(template, argumentList) + " = " + DisassembledInstruction.toHexString(internalResult));
            }
            if (components.contains(AssemblyTestComponent.DISASSEMBLER) && template.isDisassemblable() &&
                    !findExcludedDisassemblerTestArgument(template.parameters(), argumentList)) {
                try {
                    testDisassembler(template, argumentList, internalResult);
                } catch (IOException e) {
                    throw new AssemblyException(e.toString());
                }
            }

            if (testingExternally && !findExcludedExternalTestArgument(template.parameters(), argumentList)) {
                final byte[] externalResult = readExternalInstruction(externalInputStream, template, internalResult);
                for (int i = 0; i < externalResult.length; i++) {
                    if (internalResult[i] != externalResult[i]) {
                        System.err.println("external assembler test case " + testCaseNumber + " failed for template: " + template);
                        System.err.println("arguments: " + argumentList);
                        System.err.println("internal result: " + DisassembledInstruction.toHexString(internalResult));
                        System.err.println("external result: " + DisassembledInstruction.toHexString(externalResult));
                        System.err.println("internal result fields: " + disassembleFields(template, internalResult));
                        System.err.println("external result fields: " + disassembleFields(template, externalResult));
                        ProgramError.unexpected("mismatch between internal and external assembler");
                    }
                }
            }
            ++testCaseNumber;
        }

        // Process illegal test cases
        int illegalTestCaseNumber = 0;
        final Set<String> uniqueExceptionMessages = new HashSet<>();
        for (TestCaseLegality testCaseLegality : new TestCaseLegality[]{TestCaseLegality.ILLEGAL_BY_CONSTRAINT, TestCaseLegality.ILLEGAL_BY_ARGUMENT}) {
            for (final ArgumentListIterator iterator = new ArgumentListIterator(template, testCaseLegality); iterator.hasNext();) {
                final List<Argument> argumentList = iterator.next();
                final Assembler assembler = createTestAssembler();
                Trace.line(3, "assembleInternally-negative[" + illegalTestCaseNumber + "]: " + Assembly.createMethodCallString(template, argumentList));
                try {
                    assembly().assemble(assembler, template, argumentList);
                } catch (IllegalArgumentException e) {
                    final String exceptionMessage = e.getMessage();
                    uniqueExceptionMessages.add(exceptionMessage);
                    ++illegalTestCaseNumber;
                    continue;
                }

                System.err.println("illegal assembler test case " + illegalTestCaseNumber + " did not throw an exception for template: " + template);
                System.err.println("arguments: " + argumentList);
                ProgramError.unexpected("failed illegal test case");
            }
        }

        Trace.line(2, "template: " + template + "  [" + testCaseNumber + " test cases, timings: " +
                        ", " + illegalTestCaseNumber + " illegal test cases]");
        for (String message : uniqueExceptionMessages) {
            Trace.line(2, "    caught expected IllegalArgumentException: " + message);
        }
        if (testingExternally) {
            for (int i = 0; i < nNOPs; i++) {
                if (!readNop(externalInputStream)) {
                    ProgramError.unexpected("end pattern missing in: " + binaryFile.getAbsolutePath());
                }
            }
            externalInputStream.close();
        }
    }

    private String templatePattern;

    /**
     * Sets the pattern that restricts which templates are tested.
     *
     * @param pattern if non-null, only templates whose {@link Template#internalName() name} contains {@code pattern} as
     *            a substring are tested
     */
    public void setTemplatePattern(String pattern) {
        templatePattern = pattern;
    }

    /**
     * Tests a range of templates.
     *
     * @param startTemplateSerial the {@linkplain Template#serial() serial} number of the first template to test or -1
     *            to start with the first template of the {@linkplain #assembly() assembly}.
     * @param endTemplateSerial the {@linkplain Template#serial() serial} number of the last template to test or -1 to
     *            end with the last template of the {@linkplain #assembly() assembly}.
     * @param parallelize specifies if the testing should be parallelized
     */
    public void run(int startTemplateSerial, int endTemplateSerial, boolean parallelize) {

        if (components.contains(AssemblyTestComponent.EXTERNAL_ASSEMBLER)) {
            try {
                Runtime.getRuntime().exec(assemblerCommand());
            } catch (IOException ioException) {
                throw ProgramError.unexpected("Could not execute external assembler command '" + assemblerCommand() + "'", ioException);
            }
        }

        final int numberOfWorkerThreads;
        if (remoteUserAndHost != null) {
            // Don't go parallel when using ssh as the max number of ssh connections on the remote host will most likely be a problem
            numberOfWorkerThreads = 1;
        } else {
            numberOfWorkerThreads = Runtime.getRuntime().availableProcessors();
        }
        final ThreadPoolExecutor compilerService = (ThreadPoolExecutor) Executors.newFixedThreadPool(numberOfWorkerThreads);

        final CompletionService<Template_Type> compilationCompletionService = new ExecutorCompletionService<>(compilerService);
        long submittedTests = 0;
        final List<Template_Type> errors = new LinkedList<>();
        for (final Template_Type template : assembly().templates()) {
            if (template.serial() > endTemplateSerial) {
                break;
            }
            Trace.on(2);
            if (!template.isRedundant() && template.serial() >= startTemplateSerial) {
                if (templatePattern == null || template.internalName().contains(templatePattern)) {
                    ++submittedTests;
                    compilationCompletionService.submit(new Callable<Template_Type>() {
                        public Template_Type call() {
                            final List<File> temporaryFiles = new ArrayList<>();
                            try {
                                testTemplate(template, temporaryFiles);
                            } catch (Throwable throwable) {
                                Trace.line(2, "template: " + template + " failed testing");
                                throwable.printStackTrace();
                                synchronized (errors) {
                                    errors.add(template);
                                }
                            } finally {
                                for (File temporaryFile : temporaryFiles) {
                                    if (!temporaryFile.delete()) {
                                        ProgramWarning.message("could not delete temporary file: " + temporaryFile.getAbsolutePath());
                                    }
                                }
                            }
                            return template;
                        }
                    });
                }
            }
        }

        long completedTests = 0;
        while (completedTests < submittedTests) {
            try {
                final Template_Type template = compilationCompletionService.take().get();
                if (numberOfWorkerThreads > 1) {
                    Trace.line(1, "complete: " + template);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException executionException) {
                throw ProgramError.unexpected(executionException.getCause());
            }
            ++completedTests;
        }

        compilerService.shutdown();

        if (!errors.isEmpty()) {
            System.err.println("Errors occurred when testing the following templates:");
            for (Template_Type template : errors) {
                System.err.println("    " + template);
            }
            throw ProgramError.unexpected(errors.size() + " templates failed testing: see previous stack dumps in test output");
        }
    }

    /**
     * Creates external assembler source for testing a range of templates.
     *
     * @param startTemplateSerial
     *            the {@linkplain Template#serial() serial} number of the first template to test or -1 to start with the
     *            first template of the {@linkplain #assembly() assembly}.
     * @param endTemplateSerial
     *            the {@linkplain Template#serial() serial} number of the last template to test or -1 to end with the
     *            last template of the {@linkplain #assembly() assembly}.
     * @param stream
     *            where to print the generate source. The caller takes responsibility for closing the stream.
     */
    public void createExternalSource(int startTemplateSerial, int endTemplateSerial, IndentWriter stream) {
        final List<Template_Type> errors = new LinkedList<>();

        for (Template_Type template : assembly().templates()) {
            if (template.serial() > endTemplateSerial) {
                break;
            }
            Trace.on(2);
            if (!template.isRedundant() && template.serial() >= startTemplateSerial) {
                if (templatePattern == null || template.internalName().contains(templatePattern)) {
                    createExternalSource(template, stream);
                }
            }
        }

        if (!errors.isEmpty()) {
            System.err.println("Errors occurred when creating external assembler test cases for the following templates:");
            for (Template_Type template : errors) {
                System.err.println("    " + template);
            }
            throw ProgramError.unexpected(errors.size() + " templates failed testing: see previous stack dumps in test output");
        }
    }
}
