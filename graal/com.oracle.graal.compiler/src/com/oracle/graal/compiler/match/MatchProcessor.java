/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.match;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import javax.annotation.processing.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import javax.tools.Diagnostic.Kind;
import javax.tools.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;

/**
 * Processes classes annotated with {@link MatchRule}. A {@link MatchStatementSet} service is
 * generated for each top level class containing at least one such field. These service objects can
 * be retrieved as follows:
 *
 * <pre>
 *     ServiceLoader<MatchStatementSet> sl = ServiceLoader.loadInstalled(MatchStatementSet.class);
 *     for (MatchStatementSet rules : sl) {
 *         ...
 *     }
 * </pre>
 */
@SupportedAnnotationTypes({"com.oracle.graal.compiler.match.MatchRule", "com.oracle.graal.compiler.match.MatchRules", "com.oracle.graal.compiler.match.MatchableNode",
                "com.oracle.graal.compiler.match.MatchableNodes"})
public class MatchProcessor extends AbstractProcessor {

    public MatchProcessor() {
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    private final Set<Element> processedMatchRule = new HashSet<>();
    private final Set<Element> processedMatchableNode = new HashSet<>();

    private static class RuleParseError extends RuntimeException {
        private static final long serialVersionUID = 6456128283609257490L;

        RuleParseError(String format, Object... args) {
            super(String.format(format, args));
        }
    }

    private static Pattern tokenizer = Pattern.compile("\\s*([()=]|[A-Za-z][A-Za-z0-9]*)\\s*");

    private class RuleParser {
        private ArrayList<TypeDescriptor> capturedTypes = new ArrayList<>();

        private ArrayList<String> capturedNames = new ArrayList<>();

        private final String[] tokens;

        private int current;

        private MatchDescriptor matchDescriptor;

        private final Set<Element> originatingElements = new HashSet<>();

        RuleParser(String rule) {
            Matcher m = tokenizer.matcher(rule);
            List<String> list = new ArrayList<>();
            int end = 0;
            while (m.lookingAt()) {
                list.add(m.group(1));
                end = m.end();
                m.region(m.end(), m.regionEnd());
            }
            if (end != m.regionEnd()) {
                throw new RuleParseError("Unexpected tokens :" + rule.substring(m.end(), m.regionEnd()));
            }
            tokens = list.toArray(new String[0]);

            matchDescriptor = parseExpression();
            if (!done()) {
                throw new RuleParseError("didn't consume all tokens");
            }
            capturedNames.add(0, "root");
            capturedTypes.add(0, matchDescriptor.nodeType);
        }

        String next() {
            return tokens[current++];
        }

        String peek(String name) {
            if (current >= tokens.length) {
                if (name == null) {
                    throw new RuleParseError("Out of tokens");
                }
                throw new RuleParseError("Out of tokens looking for %s", name);
            }
            return tokens[current];
        }

        boolean done() {
            return current == tokens.length;
        }

        private MatchDescriptor parseExpression() {
            if (peek("(").equals("(")) {
                next();
                MatchDescriptor descriptor = parseType(true);
                for (int n = 0; n < descriptor.nodeType.inputs.length; n++) {
                    if (peek("(").equals("(")) {
                        descriptor.inputs[n] = parseExpression();
                    } else {
                        descriptor.inputs[n] = parseType(false);
                    }
                }
                for (int n = 0; n < descriptor.nodeType.inputs.length; n++) {
                    if (descriptor.inputs[n] == null) {
                        throw new RuleParseError("not enough inputs for " + descriptor.name);
                    }
                }
                if (peek(")").equals(")")) {
                    next();
                    return descriptor;
                }
                throw new RuleParseError("Too many arguments to " + descriptor.nodeType.nodeClass);
            }
            throw new RuleParseError("Extra tokens following match pattern: " + peek(null));
        }

        private MatchDescriptor parseType(boolean forExpression) {
            TypeDescriptor type = null;
            String name = null;
            if (Character.isUpperCase(peek("node type or name").charAt(0))) {
                String token = next();
                type = knownTypes.get(token);
                if (type == null) {
                    throw new RuleParseError("Unknown node type: " + token);
                }
                if (peek("=").equals("=")) {
                    next();
                    name = next();
                }
                originatingElements.addAll(type.originatingElements);
            } else if (Character.isLowerCase(peek("name").charAt(0))) {
                name = next();
                type = valueType;
            } else {
                throw new RuleParseError("Unexpected token \"%s\" when looking for name or node type", peek(null));
            }
            if (name != null) {
                if (!capturedNames.contains(name)) {
                    capturedNames.add(name);
                    capturedTypes.add(type);
                } else {
                    int index = capturedNames.indexOf(name);
                    if (capturedTypes.get(index) != type) {
                        throw new RuleParseError("Captured node \"%s\" has differing types", name);
                    }
                }
            }
            return new MatchDescriptor(type, name, forExpression);
        }

        List<String> generateVariants() {
            return matchDescriptor.generateVariants();
        }

        /**
         * Recursively accumulate any required NodeClass.Position declarations.
         */
        void generatePositionDeclarations(Set<String> declarations) {
            matchDescriptor.generatePositionDeclarations(declarations);
        }

        /**
         *
         * @return the list of node types which are captured by name
         */
        public ArrayList<TypeDescriptor> capturedTypes() {
            return capturedTypes;
        }

        public ArrayList<String> capturedNames() {
            return capturedNames;
        }
    }

    /**
     * Set to true to enable logging to a local file during annotation processing. There's no normal
     * channel for any debug messages and debugging annotation processors requires some special
     * setup.
     */
    private static final boolean DEBUG = false;

    private static final String MATCHPROCESSOR_LOG = "/tmp/matchprocessor.log";

    private static PrintWriter log;

    /**
     * Logging facility for the debugging the annotation processor.
     */

    private static synchronized PrintWriter getLog() {
        if (log == null) {
            try {
                log = new PrintWriter(new FileWriter(MATCHPROCESSOR_LOG, true));
            } catch (IOException e) {
                // Do nothing
            }
        }
        return log;
    }

    private static synchronized void logMessage(String format, Object... args) {
        if (!DEBUG) {
            return;
        }
        PrintWriter bw = getLog();
        if (bw != null) {
            bw.printf(format, args);
            bw.flush();
        }
    }

    private static synchronized void logException(Throwable t) {
        if (!DEBUG) {
            return;
        }
        PrintWriter bw = getLog();
        if (bw != null) {
            t.printStackTrace(bw);
            bw.flush();
        }
    }

    /**
     * Bugs in an annotation processor can cause silent failure so try to report any exception
     * throws as errors.
     */
    private void reportExceptionThrow(Element element, Throwable t) {
        logMessage("throw for %s:\n", element);
        logException(t);
        processingEnv.getMessager().printMessage(Kind.ERROR, "Exception throw during processing: " + t.toString() + " " + Arrays.toString(Arrays.copyOf(t.getStackTrace(), 4)), element);
    }

    static class TypeDescriptor {
        final TypeMirror mirror;

        /**
         * The name uses in match expressions to refer to this type.
         */
        final String shortName;

        /**
         * The simple name of the {@link ValueNode} class represented by this type.
         */
        final String nodeClass;

        /**
         * The package of {@link ValueNode} class represented by this type.
         */
        final String nodePackage;

        /**
         * The matchable inputs of the node.
         */
        final String[] inputs;

        /**
         * Should swapped variants of this match be generated. The user of the match is expected to
         * compensate for any ordering differences in compare which are commutative but require
         * reinterpreting the condition in that case.
         */
        final boolean commutative;

        /**
         * Can multiple users of this node subsume it. Constants can be swallowed into a match even
         * if there are multiple users.
         */
        final boolean shareable;

        final Set<Element> originatingElements = new HashSet<>();

        TypeDescriptor(TypeMirror mirror, String shortName, String nodeClass, String nodePackage, String[] inputs, boolean commutative, boolean shareable) {
            this.mirror = mirror;
            this.shortName = shortName;
            this.nodeClass = nodeClass;
            this.nodePackage = nodePackage;
            this.inputs = inputs;
            this.commutative = commutative;
            this.shareable = shareable;
            assert !commutative || inputs.length == 2;
        }
    }

    /**
     * The types which are know for purpose of parsing MatchRule expressions.
     */
    Map<String, TypeDescriptor> knownTypes = new HashMap<>();

    /**
     * The set of packages which must be imported to refer to the known classes.
     */
    List<String> requiredPackages = new ArrayList<>();

    /**
     * The java.lang.reflect.Method for invoking a method based MatchRule.
     */
    private Map<ExecutableElement, MethodInvokerItem> invokers = new LinkedHashMap<>();

    private TypeDescriptor valueType;

    private TypeMirror matchRulesTypeMirror;

    private TypeMirror matchRuleTypeMirror;

    private TypeMirror matchableNodeTypeMirror;

    private TypeMirror matchableNodesTypeMirror;

    private void declareType(TypeMirror mirror, String shortName, String nodeClass, String nodePackage, String[] inputs, boolean commutative, boolean shareable, Element element) {
        TypeDescriptor descriptor = new TypeDescriptor(mirror, shortName, nodeClass, nodePackage, inputs, commutative, shareable);
        descriptor.originatingElements.add(element);
        knownTypes.put(shortName, descriptor);
        if (!requiredPackages.contains(descriptor.nodePackage)) {
            requiredPackages.add(descriptor.nodePackage);
        }
    }

    private String findPackage(Element type) {
        PackageElement p = processingEnv.getElementUtils().getPackageOf(type);
        if (p != null) {
            return p.getQualifiedName().toString();
        }
        throw new GraalInternalError("can't find package for %s", type);
    }

    class MatchDescriptor {
        TypeDescriptor nodeType;
        String name;
        MatchDescriptor[] inputs;

        MatchDescriptor(TypeDescriptor nodeType, String name, boolean forExpression) {
            this.nodeType = nodeType;
            this.name = name;
            if (forExpression) {
                this.inputs = new MatchDescriptor[nodeType.inputs.length];
            } else {
                this.inputs = new MatchDescriptor[0];
            }
        }

        public void generatePositionDeclarations(Set<String> declarations) {
            if (inputs.length == 0) {
                return;
            }
            declarations.add(generatePositionDeclaration());
            for (MatchDescriptor desc : inputs) {
                desc.generatePositionDeclarations(declarations);
            }
        }

        List<String> recurseVariants(int index) {
            if (inputs.length == 0) {
                return new ArrayList<>();
            }
            List<String> currentVariants = inputs[index].generateVariants();
            if (index == inputs.length - 1) {
                return currentVariants;
            }
            List<String> subVariants = recurseVariants(index + 1);
            List<String> result = new ArrayList<>();
            for (String current : currentVariants) {
                for (String sub : subVariants) {
                    result.add(current + ", " + sub);
                    if (nodeType.commutative) {
                        result.add(sub + ", " + current);
                    }
                }
            }
            return result;
        }

        /**
         * Recursively generate all the variants of this rule pattern. Currently that just means to
         * swap the inputs for commutative rules, producing all possible permutations.
         *
         * @return a list of Strings which will construct pattern matchers for this rule.
         */
        List<String> generateVariants() {
            String prefix = formatPrefix();
            String suffix = formatSuffix();
            ArrayList<String> variants = new ArrayList<>();
            if (inputs.length > 0) {
                for (String var : recurseVariants(0)) {
                    variants.add(prefix + ", " + var + suffix);
                }
            } else {
                assert inputs.length == 0;
                variants.add(prefix + suffix);
            }

            return variants;
        }

        private String formatPrefix() {
            if (nodeType == valueType) {
                return String.format("new MatchPattern(%s, false", name != null ? ("\"" + name + "\"") : "null");
            } else {
                return String.format("new MatchPattern(%s.class, %s", nodeType.nodeClass, name != null ? ("\"" + name + "\"") : "null");
            }
        }

        private String formatSuffix() {
            if (nodeType != null) {
                if (inputs.length != nodeType.inputs.length) {
                    return ", true)";
                } else {
                    if (nodeType.inputs.length > 0) {
                        return ", " + nodeType.nodeClass + "_positions, " + !nodeType.shareable + ")";
                    }
                    if (nodeType.shareable) {
                        return ", false)";
                    }
                }
            }
            return ")";
        }

        String generatePositionDeclaration() {
            return String.format("private static final NodeClass.Position[] %s_positions = MatchPattern.findPositions(%s.class, new String[]{\"%s\"});", nodeType.nodeClass, nodeType.nodeClass,
                            String.join("\", \"", nodeType.inputs));
        }
    }

    /**
     * Strip the package off a class name leaving the full class name including any outer classes.
     */
    private String fullClassName(Element element) {
        assert element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.INTERFACE : element;
        String pkg = findPackage(element);
        return ((TypeElement) element).getQualifiedName().toString().substring(pkg.length() + 1);
    }

    private void createFiles(MatchRuleDescriptor info) {
        String pkg = ((PackageElement) info.topDeclaringType.getEnclosingElement()).getQualifiedName().toString();
        Name topDeclaringClass = info.topDeclaringType.getSimpleName();

        String matchStatementClassName = topDeclaringClass + "_" + MatchStatementSet.class.getSimpleName();
        Element[] originatingElements = info.originatingElements.toArray(new Element[info.originatingElements.size()]);

        Types typeUtils = typeUtils();
        Filer filer = processingEnv.getFiler();
        try (PrintWriter out = createSourceFile(pkg, matchStatementClassName, filer, originatingElements)) {

            out.println("// CheckStyle: stop header check");
            out.println("// GENERATED CONTENT - DO NOT EDIT");
            out.println("// Source: " + topDeclaringClass + ".java");
            out.println("package " + pkg + ";");
            out.println("");
            out.println("import java.util.*;");
            out.println("import java.lang.reflect.*;");
            out.println("import " + MatchStatementSet.class.getPackage().getName() + ".*;");
            out.println("import " + GraalInternalError.class.getName() + ";");
            out.println("import " + NodeLIRBuilder.class.getName() + ";");
            out.println("import " + NodeClass.class.getName() + ";");
            for (String p : requiredPackages) {
                out.println("import " + p + ".*;");
            }
            out.println("");
            out.println("public class " + matchStatementClassName + " implements " + MatchStatementSet.class.getSimpleName() + " {");

            out.println();
            out.println("    private static Method lookupMethod(Class<?> theClass, String name, Class<?>... args) {");
            out.println("        try {");
            out.println("            return theClass.getDeclaredMethod(name, args);");
            out.println("        } catch (Exception e) {");
            out.println("            throw new GraalInternalError(e);");
            out.println("        }");
            out.println("    }");
            out.println();

            // Generate declarations for the reflective invocation of the code generation methods.
            for (MethodInvokerItem invoker : invokers.values()) {
                StringBuilder args = new StringBuilder();
                StringBuilder types = new StringBuilder();
                int count = invoker.fields.size();
                for (VariableElement arg : invoker.fields) {
                    args.append('"');
                    args.append(arg.getSimpleName());
                    args.append('"');
                    types.append(fullClassName(typeUtils.asElement(arg.asType())));
                    types.append(".class");
                    if (count-- > 1) {
                        args.append(", ");
                        types.append(", ");
                    }
                }
                out.printf("    private static final String[] %s = new String[] {%s};\n", invoker.argumentsListName(), args);
                out.printf("    private static final Method %s = lookupMethod(%s.class, \"%s\", %s);\n", invoker.reflectiveMethodName(), invoker.nodeLIRBuilderClass, invoker.methodName, types);
                out.println();

            }

            for (String positionDeclaration : info.positionDeclarations) {
                out.println("    " + positionDeclaration);
            }
            out.println();

            String desc = MatchStatement.class.getSimpleName();
            out.println("    // CheckStyle: stop line length check");
            out.println("    private static final List<" + desc + "> statements = Collections.unmodifiableList(Arrays.asList(");

            int i = 0;
            for (MatchRuleItem matchRule : info.matchRules) {
                String comma = i == info.matchRules.size() - 1 ? "" : ",";
                out.printf("        %s%s\n", matchRule.ruleBuilder(), comma);
                i++;
            }
            out.println("    ));");
            out.println("    // CheckStyle: resume line length check");
            out.println();

            out.println("    public Class<? extends NodeLIRBuilder> forClass() {");
            out.println("        return " + topDeclaringClass + ".class;");
            out.println("    }");
            out.println();
            out.println("    @Override");
            out.println("    public List<" + desc + "> statements() {");
            out.println("        return statements;");
            out.println("    }");
            out.println("}");
        }

        try {
            createProviderFile(pkg, matchStatementClassName, originatingElements);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Kind.ERROR, e.getMessage(), info.topDeclaringType);
        }
    }

    private void createProviderFile(String pkg, String providerClassName, Element... originatingElements) throws IOException {
        String filename = "META-INF/providers/" + pkg + "." + providerClassName;
        FileObject file = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", filename, originatingElements);
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(file.openOutputStream(), "UTF-8"));
        writer.println(MatchStatementSet.class.getName());
        writer.close();
    }

    protected PrintWriter createSourceFile(String pkg, String relativeName, Filer filer, Element... originatingElements) {
        try {
            // Ensure Unix line endings to comply with Graal code style guide checked by Checkstyle
            JavaFileObject sourceFile = filer.createSourceFile(pkg + "." + relativeName, originatingElements);
            return new PrintWriter(sourceFile.openWriter()) {

                @Override
                public void println() {
                    print("\n");
                }
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Used to generate the MatchStatement constructor invocation.
     */
    static class MatchRuleItem {
        private final String matchPattern;
        private final MethodInvokerItem invoker;

        public MatchRuleItem(String matchPattern, MethodInvokerItem invoker) {
            this.matchPattern = matchPattern;
            this.invoker = invoker;
        }

        /**
         * @return a string which will construct the MatchStatement instance to match this pattern.
         */
        public String ruleBuilder() {
            return String.format("new MatchStatement(\"%s\", %s, %s, %s)", invoker.name, matchPattern, invoker.reflectiveMethodName(), invoker.argumentsListName());
        }
    }

    /**
     * Used to generate the declarations needed for reflective invocation of the code generation
     * method.
     */
    static class MethodInvokerItem {
        final String name;
        final String nodeLIRBuilderClass;
        final String methodName;
        final List<? extends VariableElement> fields;

        MethodInvokerItem(String name, String nodeLIRBuilderClass, String methodName, List<? extends VariableElement> fields) {
            this.name = name;
            this.nodeLIRBuilderClass = nodeLIRBuilderClass;
            this.methodName = methodName;
            this.fields = fields;
        }

        String reflectiveMethodName() {
            return methodName + "_invoke";
        }

        String argumentsListName() {
            return methodName + "_arguments";
        }
    }

    static class MatchRuleDescriptor {

        final TypeElement topDeclaringType;
        final List<MatchRuleItem> matchRules = new ArrayList<>();
        private final Set<Element> originatingElements = new HashSet<>();
        public Set<String> positionDeclarations = new LinkedHashSet<>();

        public MatchRuleDescriptor(TypeElement topDeclaringType) {
            this.topDeclaringType = topDeclaringType;
        }
    }

    private static TypeElement topDeclaringType(Element element) {
        Element enclosing = element.getEnclosingElement();
        if (enclosing == null || enclosing.getKind() == ElementKind.PACKAGE) {
            assert element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.INTERFACE;
            return (TypeElement) element;
        }
        return topDeclaringType(enclosing);
    }

    private AnnotationMirror findAnnotationMirror(Element element, TypeMirror typeMirror) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (typeUtils().isSameType(mirror.getAnnotationType(), typeMirror)) {
                return mirror;
            }
        }
        return null;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }
        logMessage("Starting round %s\n", roundEnv);
        matchRulesTypeMirror = processingEnv.getElementUtils().getTypeElement(MatchRules.class.getCanonicalName()).asType();
        matchRuleTypeMirror = processingEnv.getElementUtils().getTypeElement(MatchRule.class.getCanonicalName()).asType();

        matchableNodeTypeMirror = processingEnv.getElementUtils().getTypeElement(MatchableNode.class.getCanonicalName()).asType();
        matchableNodesTypeMirror = processingEnv.getElementUtils().getTypeElement(MatchableNodes.class.getCanonicalName()).asType();

        try {
            // Define a TypeDescriptor for the generic node but don't enter it into the nodeTypes
            // table since it shouldn't be mentioned in match rules.
            TypeMirror valueTypeMirror = processingEnv.getElementUtils().getTypeElement(ValueNode.class.getName()).asType();
            valueType = new TypeDescriptor(valueTypeMirror, "Value", ValueNode.class.getSimpleName(), ValueNode.class.getPackage().getName(), new String[0], false, false);

            Map<TypeElement, MatchRuleDescriptor> map = new LinkedHashMap<>();

            for (Element element : roundEnv.getElementsAnnotatedWith(MatchRule.class)) {
                processMatchRule(map, element, findAnnotationMirror(element, matchRuleTypeMirror));
            }
            for (Element element : roundEnv.getElementsAnnotatedWith(MatchRules.class)) {
                processMatchRule(map, element, findAnnotationMirror(element, matchRulesTypeMirror));
            }

            for (MatchRuleDescriptor info : map.values()) {
                createFiles(info);
            }

        } catch (Throwable t) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Exception throw during processing: " + t.toString() + " " + Arrays.toString(Arrays.copyOf(t.getStackTrace(), 2)));
        }

        return true;
    }

    /**
     * Build up the type table to be used during parsing of the MatchRule.
     */
    private void processMatchableNode(Element element) {
        if (!processedMatchableNode.contains(element)) {
            try {
                processedMatchableNode.add(element);

                AnnotationMirror mirror = findAnnotationMirror(element, matchableNodesTypeMirror);
                if (mirror == null) {
                    mirror = findAnnotationMirror(element, matchableNodeTypeMirror);
                }
                if (mirror == null) {
                    return;
                }
                TypeElement topDeclaringType = topDeclaringType(element);
                List<AnnotationMirror> mirrors = null;
                if (typeUtils().isSameType(mirror.getAnnotationType(), matchableNodesTypeMirror)) {
                    // Unpack the mirrors for a repeatable annotation
                    mirrors = getAnnotationValueList(AnnotationMirror.class, mirror, "value");
                }
                int i = 0;
                for (MatchableNode matchableNode : element.getAnnotationsByType(MatchableNode.class)) {
                    processMatchableNode(element, topDeclaringType, matchableNode, mirrors != null ? mirrors.get(i++) : mirror);
                }
            } catch (Throwable t) {
                reportExceptionThrow(element, t);
            }
        }
    }

    private void processMatchableNode(Element element, TypeElement topDeclaringType, MatchableNode matchable, AnnotationMirror mirror) throws GraalInternalError {
        logMessage("processMatchableNode %s %s %s\n", topDeclaringType, element, matchable);
        String nodeClass;
        String nodePackage;
        TypeMirror nodeClassMirror = null;
        try {
            matchable.nodeClass();
        } catch (MirroredTypeException e) {
            nodeClassMirror = e.getTypeMirror();
        }
        if (nodeClassMirror == null) {
            throw new GraalInternalError("Can't get mirror for node class %s", element);
        }
        if (nodeClassMirror.toString().equals(MatchableNode.class.getName())) {
            nodeClass = topDeclaringType.getQualifiedName().toString();
        } else {
            nodeClass = nodeClassMirror.toString();
        }
        nodePackage = findPackage(processingEnv.getElementUtils().getTypeElement(nodeClass));
        assert nodeClass.startsWith(nodePackage);
        nodeClass = nodeClass.substring(nodePackage.length() + 1);
        assert nodeClass.endsWith("Node");
        String shortName = nodeClass.substring(0, nodeClass.length() - 4);

        Types typeUtils = processingEnv.getTypeUtils();
        TypeElement nodeClassElement = (TypeElement) typeUtils.asElement(nodeClassMirror);
        for (String input : matchable.inputs()) {
            boolean ok = false;
            TypeElement current = nodeClassElement;
            while (!ok && current != null) {
                for (Element fieldElement : ElementFilter.fieldsIn(current.getEnclosedElements())) {
                    if (fieldElement.getSimpleName().toString().equals(input)) {
                        ok = true;
                        break;
                    }
                }
                TypeMirror theSuper = current.getSuperclass();
                current = (TypeElement) typeUtils.asElement(theSuper);
            }
            if (!ok) {
                String msg = String.format("Input named \"%s\" doesn't exist in %s", input, nodeClassElement.getSimpleName());
                processingEnv.getMessager().printMessage(Kind.ERROR, msg, element, mirror);
            }
        }

        declareType(nodeClassMirror, shortName, nodeClass, nodePackage, matchable.inputs(), matchable.commutative(), matchable.shareable(), element);
    }

    private void processMatchRule(Map<TypeElement, MatchRuleDescriptor> map, Element element, AnnotationMirror mirror) {
        if (!processedMatchRule.contains(element)) {
            try {
                processedMatchRule.add(element);

                // The annotation element type should ensure this is true.
                assert element instanceof ExecutableElement;

                findMatchableNodes(element);

                TypeElement topDeclaringType = topDeclaringType(element);
                MatchRuleDescriptor info = map.get(topDeclaringType);
                if (info == null) {
                    info = new MatchRuleDescriptor(topDeclaringType);
                    map.put(topDeclaringType, info);
                }
                List<AnnotationMirror> mirrors = null;
                if (typeUtils().isSameType(mirror.getAnnotationType(), matchRulesTypeMirror)) {
                    // Unpack the mirrors for a repeatable annotation
                    mirrors = getAnnotationValueList(AnnotationMirror.class, mirror, "value");
                }
                int i = 0;
                for (MatchRule matchRule : element.getAnnotationsByType(MatchRule.class)) {
                    processMethodMatchRule((ExecutableElement) element, info, matchRule, mirrors != null ? mirrors.get(i++) : mirror);
                }
            } catch (Throwable t) {
                reportExceptionThrow(element, t);
            }
        }
    }

    /**
     * Search the super types of element for MatchableNode definitions. Any superclass or super
     * interface can contain definitions of matchable nodes.
     *
     * @param element
     */
    private void findMatchableNodes(Element element) {
        processMatchableNode(element);
        Element enclosing = element.getEnclosingElement();
        while (enclosing != null) {
            if (enclosing.getKind() == ElementKind.CLASS || enclosing.getKind() == ElementKind.INTERFACE) {
                TypeElement current = (TypeElement) enclosing;
                while (current != null) {
                    processMatchableNode(current);
                    for (TypeMirror intf : current.getInterfaces()) {
                        Element interfaceElement = typeUtils().asElement(intf);
                        processMatchableNode(interfaceElement);
                        // Recurse
                        findMatchableNodes(interfaceElement);
                    }
                    TypeMirror theSuper = current.getSuperclass();
                    current = (TypeElement) typeUtils().asElement(theSuper);
                }
            }
            enclosing = enclosing.getEnclosingElement();
        }
    }

    private Types typeUtils() {
        return processingEnv.getTypeUtils();
    }

    private void processMethodMatchRule(ExecutableElement method, MatchRuleDescriptor info, MatchRule matchRule, AnnotationMirror mirror) {
        logMessage("processMethodMatchRule %s %s\n", method, mirror);

        Types typeUtils = typeUtils();

        if (!method.getModifiers().contains(Modifier.PUBLIC)) {
            String msg = String.format("MatchRule method %s must be public", method.getSimpleName());
            processingEnv.getMessager().printMessage(Kind.ERROR, msg, method);
            return;
        }
        if (method.getModifiers().contains(Modifier.STATIC)) {
            String msg = String.format("MatchRule method %s must be non-static", method.getSimpleName());
            processingEnv.getMessager().printMessage(Kind.ERROR, msg, method);
            return;
        }

        try {
            TypeMirror returnType = method.getReturnType();
            if (!typeUtils.isSameType(returnType, processingEnv.getElementUtils().getTypeElement(ComplexMatchResult.class.getName()).asType())) {
                String msg = String.format("MatchRule method return type must be %s", ComplexMatchResult.class.getName());
                processingEnv.getMessager().printMessage(Kind.ERROR, msg, method);
                return;
            }

            String rule = matchRule.value();
            RuleParser parser = new RuleParser(rule);
            ArrayList<TypeDescriptor> expectedTypes = parser.capturedTypes();
            ArrayList<String> expectedNames = parser.capturedNames();
            List<? extends VariableElement> actualParameters = method.getParameters();
            if (expectedTypes.size() + 1 < actualParameters.size()) {
                String msg = String.format("Too many arguments for match method %s %s", expectedTypes.size() + 1, actualParameters.size());
                processingEnv.getMessager().printMessage(Kind.ERROR, msg, method);
                return;
            }

            // Walk through the parameters to the method and see if they exist in the match rule.
            // The order doesn't matter but only names mentioned in the rule can be used and they
            // must be assignment compatible.
            for (VariableElement parameter : actualParameters) {
                String name = parameter.getSimpleName().toString();
                int nameIndex = expectedNames.indexOf(name);
                if (nameIndex == -1) {
                    String msg = String.format("Argument \"%s\" isn't captured in the match rule", name);
                    processingEnv.getMessager().printMessage(Kind.ERROR, msg, method);
                    return;
                }
                TypeMirror type = parameter.asType();
                if (!typeUtils.isAssignable(expectedTypes.get(nameIndex).mirror, type)) {
                    String msg = String.format("Captured value \"%s\" of type %s is not assignable to argument of type %s", name, expectedTypes.get(nameIndex).mirror, type);
                    processingEnv.getMessager().printMessage(Kind.ERROR, msg, method);
                    return;
                }
            }

            MethodInvokerItem invoker = invokers.get(method);
            if (invoker == null) {
                invoker = new MethodInvokerItem(method.getSimpleName().toString(), topDeclaringType(method).getSimpleName().toString(), method.getSimpleName().toString(), actualParameters);
                invokers.put(method, invoker);
            }

            Element enclosing = method.getEnclosingElement();
            String declaringClass = "";
            String separator = "";
            Set<Element> originatingElementsList = info.originatingElements;
            originatingElementsList.add(method);
            while (enclosing != null) {
                if (enclosing.getKind() == ElementKind.CLASS || enclosing.getKind() == ElementKind.INTERFACE) {
                    if (enclosing.getModifiers().contains(Modifier.PRIVATE)) {
                        String msg = String.format("MatchRule cannot be declared in a private %s %s", enclosing.getKind().name().toLowerCase(), enclosing);
                        processingEnv.getMessager().printMessage(Kind.ERROR, msg, method);
                        return;
                    }
                    originatingElementsList.add(enclosing);
                    declaringClass = enclosing.getSimpleName() + separator + declaringClass;
                    separator = ".";
                } else {
                    assert enclosing.getKind() == ElementKind.PACKAGE;
                }
                enclosing = enclosing.getEnclosingElement();
            }

            originatingElementsList.addAll(parser.originatingElements);

            // Accumulate any position declarations.
            parser.generatePositionDeclarations(info.positionDeclarations);

            List<String> matches = parser.generateVariants();
            for (String match : matches) {
                info.matchRules.add(new MatchRuleItem(match, invoker));
            }
        } catch (RuleParseError e) {
            processingEnv.getMessager().printMessage(Kind.ERROR, e.getMessage(), method, mirror);
        }
    }

    // TODO borrowed from com.oracle.truffle.dsl.processor.Utils
    @SuppressWarnings("unchecked")
    private static <T> List<T> getAnnotationValueList(Class<T> expectedListType, AnnotationMirror mirror, String name) {
        List<? extends AnnotationValue> values = getAnnotationValue(List.class, mirror, name);
        List<T> result = new ArrayList<>();

        if (values != null) {
            for (AnnotationValue value : values) {
                T annotationValue = resolveAnnotationValue(expectedListType, value);
                if (annotationValue != null) {
                    result.add(annotationValue);
                }
            }
        }
        return result;
    }

    private static <T> T getAnnotationValue(Class<T> expectedType, AnnotationMirror mirror, String name) {
        return resolveAnnotationValue(expectedType, getAnnotationValue(mirror, name));
    }

    @SuppressWarnings({"unchecked"})
    private static <T> T resolveAnnotationValue(Class<T> expectedType, AnnotationValue value) {
        if (value == null) {
            return null;
        }

        Object unboxedValue = value.accept(new AnnotationValueVisitorImpl(), null);
        if (unboxedValue != null) {
            if (expectedType == TypeMirror.class && unboxedValue instanceof String) {
                return null;
            }
            if (!expectedType.isAssignableFrom(unboxedValue.getClass())) {
                throw new ClassCastException(unboxedValue.getClass().getName() + " not assignable from " + expectedType.getName());
            }
        }
        return (T) unboxedValue;
    }

    private static AnnotationValue getAnnotationValue(AnnotationMirror mirror, String name) {
        ExecutableElement valueMethod = null;
        for (ExecutableElement method : ElementFilter.methodsIn(mirror.getAnnotationType().asElement().getEnclosedElements())) {
            if (method.getSimpleName().toString().equals(name)) {
                valueMethod = method;
                break;
            }
        }

        if (valueMethod == null) {
            return null;
        }

        AnnotationValue value = mirror.getElementValues().get(valueMethod);
        if (value == null) {
            value = valueMethod.getDefaultValue();
        }

        return value;
    }

    private static class AnnotationValueVisitorImpl extends AbstractAnnotationValueVisitor7<Object, Void> {

        @Override
        public Object visitBoolean(boolean b, Void p) {
            return Boolean.valueOf(b);
        }

        @Override
        public Object visitByte(byte b, Void p) {
            return Byte.valueOf(b);
        }

        @Override
        public Object visitChar(char c, Void p) {
            return c;
        }

        @Override
        public Object visitDouble(double d, Void p) {
            return d;
        }

        @Override
        public Object visitFloat(float f, Void p) {
            return f;
        }

        @Override
        public Object visitInt(int i, Void p) {
            return i;
        }

        @Override
        public Object visitLong(long i, Void p) {
            return i;
        }

        @Override
        public Object visitShort(short s, Void p) {
            return s;
        }

        @Override
        public Object visitString(String s, Void p) {
            return s;
        }

        @Override
        public Object visitType(TypeMirror t, Void p) {
            return t;
        }

        @Override
        public Object visitEnumConstant(VariableElement c, Void p) {
            return c;
        }

        @Override
        public Object visitAnnotation(AnnotationMirror a, Void p) {
            return a;
        }

        @Override
        public Object visitArray(List<? extends AnnotationValue> vals, Void p) {
            return vals;
        }

    }
}
