/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.core.test.veriopt;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.veriopt.VeriOpt;
import jdk.graal.compiler.core.veriopt.VeriOptGraphTranslator;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.runtime.RuntimeProvider;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;

public class VeriOptGraphCache {

    private static final Backend backend = Graal.getRuntime().getRequiredCapability(RuntimeProvider.class).getHostBackend();
    private static final Providers providers = backend.getProviders();

    private static HashMap<String, CacheEntry> cache = new HashMap<>();

    private final Function<ResolvedJavaMethod, StructuredGraph> graphBuilder;

    // A mapping from the full to shorthand type names
    private static Map<String, String> typeEncodings = new HashMap<>();
    static {
        typeEncodings.put("boolean", "Z");
        typeEncodings.put("byte",    "B");
        typeEncodings.put("char",    "C");
        typeEncodings.put("double",  "D");
        typeEncodings.put("float",   "F");
        typeEncodings.put("int",     "I");
        typeEncodings.put("long",    "J");
        typeEncodings.put("short",   "S");
        typeEncodings.put("void",    "V");
    }

    public VeriOptGraphCache(Function<ResolvedJavaMethod, StructuredGraph> graphBuilder) {
        this.graphBuilder = graphBuilder;
    }

    private CacheEntry getCacheEntry(ResolvedJavaMethod method) {
    //    return cache.computeIfAbsent(VeriOpt.formatMethod(method), key -> {  // TODO: turn caching back on?
        String key = VeriOpt.formatMethod(method);
            StructuredGraph graph = null;
            String nodeArray = null;
            RuntimeException exception = null;
//  TODO: turned off use of optimised graph temporarily, because then we miss called methods.
//            try {
//                if (graphBuilder != null) {
//                    // Optimised generation (if available)
//                    graph = graphBuilder.apply(method);
//                    nodeArray = VeriOptGraphTranslator.writeNodeArray(graph);
//                }
//            } catch (Exception e) {
//                // Optimised graph may cause problems, fall through to non-optimised generation
//            }

            try {
                if (nodeArray == null) {
                    // Unoptimised generation
                    graph = buildGraph(method);
                    nodeArray = VeriOptGraphTranslator.writeNodeArray(graph);
                }

                /* TODO handling empty methods
                if (nodeArray == null && methodIsEmpty(method)) {
                    // Create an empty graph for empty methods.
                    graph = emptyGraph(method);
                    nodeArray = VeriOptGraphTranslator.writeNodeArray(graph);
                }
                */

                if (VeriOpt.DEBUG) {
                    System.out.printf("DEBUG:   key=%s for %s.%s %s graphlen=%d\n", key, method.getDeclaringClass().getName(), method.getName(), method.getSignature(), nodeArray.length());
                }
            } catch (IllegalArgumentException ex) {
                // we wrap the exception with some context information about this graph
                // then throw it again so that the top-level method dump will fail.
                exception = ex;
            }
            return new CacheEntry(graph, key, nodeArray, exception);
    //    });
    }

    /**
     * Generates and returns an empty graph containing two nodes: a start node and an empty (null) return node. Ensures
     * that references to empty interface functions are resolved properly.
     *
     * @param method the empty method for which the graph is being created.
     * @return an empty graph returning nothing.
     * */
    private StructuredGraph emptyGraph(ResolvedJavaMethod method) {
        // Get initial options and debug context
        OptionValues options = Graal.getRuntime().getRequiredCapability(OptionValues.class);
        DebugContext debugContext = new DebugContext.Builder(options, Collections.emptyList()).build();

        // Initial setup
        StructuredGraph graph = new StructuredGraph.Builder(options, debugContext).name(method.getName()).build();
        StartNode startNode = graph.start();
        FrameState frameState = new FrameState(BytecodeFrame.BEFORE_BCI);
        graph.add(frameState);
        startNode.setStateAfter(frameState);

        // Create the return node
        ReturnNode returnNode = new ReturnNode(null);
        graph.add(returnNode);

        // Link the start and return node
        startNode.setNext(returnNode);

        return graph;
    }

    /**
     * Returns whether a {@code method} has no code to generate a graph for.
     *
     * @param method the method being checked.
     * @return {@code True} if the method has no code to generate a graph for, else {@code False}.
     * */
    public static boolean methodIsEmpty(ResolvedJavaMethod method) {
        return ((!method.hasBytecodes()) || method.getCode() == null || method.getCodeSize() == 0);
    }

    /**
     * Get a StructuredGraph from the cache. If it doesn't exist, it will be built.
     *
     * @param method The method to get the graph of
     * @return The StructuredGraph
     */
    public StructuredGraph getGraph(ResolvedJavaMethod method) {
        return getCacheEntry(method).graph;
    }

    /**
     * Get an Isabelle node array for the specified method.
     *
     * @param method The method to get the node array of
     * @return The node array, or null if it can't be translated
     */
    public String getNodeArray(ResolvedJavaMethod method) {
        return getCacheEntry(method).nodeArray;
    }

    /**
     * Returns an entity's type in shorthand format, based on the encoding given in {@link Class#getName()}. If the
     * type is a class or interface, the original typeName is returned.
     *
     * @param typeName the full name of the type (e.g., "int").
     * @return the shorthand name of the type (e.g., "I").
     * */
    private static String encodeTypeName(String typeName) {
        return typeEncodings.getOrDefault(typeName, typeName);
    }

    /**
     * Returns the unique name of a {@link Method}, based on the string representation returned by
     * {@link Signature#toMethodDescriptor()}.
     *
     * @param className the name of the class that the method belongs to.
     * @param methodName the name of the method.
     * @param parameters the types of the method's parameters (in shorthand format).
     * @param returnType the method's return type (in shorthand format).
     * @return a unique name for the method of the form: "className.methodName(AB)C" for parameter types A & B, and
     *         return type C.
     * */
    private static String getUniqueMethodName(String className, String methodName, List<String> parameters,
                                              String returnType) {
        return className + "." + methodName + "(" + String.join("", parameters) + ")" + returnType;
    }

    /**
     * Removes the comma at the end of the JVMClass representation.
     *
     * @param input the JVMClass translation thus far.
     * */
    private static void removeLastComma(StringBuilder input) {
        if (input.charAt(input.length() - 1) != '[') {
            input.setLength(input.length() - 2);
        }
    }

    /**
     * Returns the path from a class up to java.lang.Object. The final entry in the parent path of every class
     * (including java.lang.Object) is "None".
     *
     * @param parent the name of the current parent in the parent path list.
     * @return a string denoting the parent path in an Isabelle string list format, without enclosing brackets
     *         (e.g., ''class1'', ''java.lang.Object'', ''None'')
     * */
    private static String getParentPath(String parent) {
        // Class is java.lang.Object
        if (Objects.equals(parent, "None")) {
            return "''None''";
        }

        // Class extends from java.lang.Object
        try {
            Class<?> nextParent = Class.forName(parent);
            String parentName = nextParent.getSuperclass() == null ? "None" : nextParent.getSuperclass().getName();
            return "''" + parent + "'', " + getParentPath(parentName);
        } catch (ClassNotFoundException c) {
            System.out.println("ClassNotFoundException: MESSAGE =" + c.getMessage());
        }

        // As long as reflection succeeds, this should never happen
        return "";
    }

    /**
     * Formats a string of parameter types into a JVMClass NewParameter format.
     *
     * @param parameterTypes the types of the parameters.
     * @return a string of the form [NewParameter ''a'', NewParameter ''b'', ...] (for types a & b).
     * */
    private static String formatParameters(List<String> parameterTypes) {
        StringBuilder paramString = new StringBuilder();
        paramString.append('[');

        for (String type : parameterTypes) {
            paramString.append("NewParameter ").append("''").append(type).append("''").append(", ");
        }

        removeLastComma(paramString);
        return paramString.append(']').toString();
    }

    /**
     * Encodes a class' relevant details into a particular JVMClass parameter format.
     *
     * @param parameterTypePrefix the prefix to construct a particular JVMClass parameter in Isabelle (one of,
     *                            "NewField", "NewMethod" or "NewConstructor").
     * @param arguments the arguments of the parameter.
     * @return a string containing the given parameter in JVMClass format (e.g., "NewField ''arg1'' ''arg2'', "
     *         or "NewConstructor [NewParameter ''a''], ").
     * */
    private static String writeJVMClassParameter(String parameterTypePrefix, List<String> arguments) {
        StringBuilder argumentsList = new StringBuilder();

        for (String argument : arguments) {
            // Arguments which are lists (i.e., method or constructor parameters) should not be enclosed in Isabelle string ('') quotations
            if ((argument.indexOf('[') == 0) && (argument.indexOf(']') == argument.length() - 1)) {
                argumentsList.append(argument).append(" ");
            } else {
                argumentsList.append("''").append(argument).append("''").append(" ");
            }
        }

        argumentsList.setLength(argumentsList.length() - 1); // Remove final space
        return parameterTypePrefix + " " + argumentsList + ", ";
    }

    /**
     * Returns an Isabelle JVMClass representation based on the class given.
     *
     * @param classToExtract the class being translated.
     * @return a string of the form "NewClass ...." representing the class in Isabelle JVMClass format.
     * */
    private static String generateClassRepresentation(Class<?> classToExtract) {
        /* Extracting class information */
        // Class name, fields, methods, constructors and parent class
        String JVMClassName = classToExtract.getName();
        Field[] fields = classToExtract.getDeclaredFields();
        Method[] methods = classToExtract.getDeclaredMethods();
        Constructor<?>[] constructors = classToExtract.getDeclaredConstructors();

        // Handle case where the class is java.lang.Object
        String JVMClassParent = (classToExtract.getSuperclass() == null) ? "None" :
                                 classToExtract.getSuperclass().getName();

        // Extract the path from this class to java.lang.Object
        String parentPath = '[' + getParentPath(JVMClassParent) + ']';

        /* Constructing the Isabelle representation */
        StringBuilder translation = new StringBuilder();
        translation.append("NewClass ''").append(JVMClassName).append("''\n");

        translation.append("\t\t[");
        for (Field field : fields) {
            translation.append(writeJVMClassParameter("NewField",
                    Arrays.asList(field.getName(), encodeTypeName(field.getType().getName()))));
        }
        removeLastComma(translation);
        translation.append("]\n");

        translation.append("\t\t[");
        for (Method method : methods) {
            List<Class<?>> parameters = Arrays.asList(method.getParameterTypes());
            List<String> paramsAsStrings = new ArrayList<>();
            parameters.forEach(parameter -> paramsAsStrings.add(encodeTypeName(parameter.getName())));

            String uniqueName = getUniqueMethodName(JVMClassName, method.getName(), paramsAsStrings,
                    encodeTypeName(method.getReturnType().getName()));

            translation.append(writeJVMClassParameter("NewMethod",
                    Arrays.asList(method.getName(), encodeTypeName(method.getReturnType().getName()),
                    formatParameters(paramsAsStrings), uniqueName)));
        }
        removeLastComma(translation);
        translation.append("]\n");

        translation.append("\t\t[");
        for (Constructor<?> constructor : constructors) {
            List<Class<?>> parameters = Arrays.asList(constructor.getParameterTypes());
            List<String> paramsAsStrings = new ArrayList<>();
            parameters.forEach(parameter -> paramsAsStrings.add(encodeTypeName(parameter.getName())));

            translation.append(writeJVMClassParameter("NewConstructor",
                    List.of(formatParameters(paramsAsStrings))));
        }
        removeLastComma(translation);
        translation.append("]\n");

        translation.append("\t\t").append(parentPath).append("\n");
        translation.append("\t\t''").append(JVMClassParent).append("''");
        return translation.toString();
    }

    /**
     * Returns an Isabelle definition containing Java classes in their JVMClass representations.
     *
     * @param classesToEncode The Java classes to be encoded into their JVMClass representation.
     * @return A string denoting an Isabelle definition that contains the classes' JVMClass representations.
     * */
    public static String generateJVMClasses(Set<String> classesToEncode) {
        // No mappings to generate
        if (classesToEncode.size() == 0) {
            return "";
        }

        // Ensure java.lang.object is always encoded
        classesToEncode.add("java.lang.Object");

        StringBuilder mapping = new StringBuilder();
        mapping.append("\n\n");
        mapping.append("definition {name}_mapping :: \"JVMClass list\" where");
        mapping.append("\n\t\"{name}_mapping = [");

        // Use reflection to get class information
        for (String classToEncode : classesToEncode) {
            try {
                Class<?> currentClass = Class.forName(classToEncode);
                String classTranslation = generateClassRepresentation(currentClass);

                mapping.append("\n\t").append(classTranslation).append(",\n");
             } catch (ClassNotFoundException c) {
                 // Reflection was unable to find the specified class
                 System.out.println("ClassNotFoundException: MESSAGE =" + c.getMessage());
            }
        }

        // Remove trailing characters
        mapping.setLength(mapping.length() - 2);
        mapping.append("]\"\n"); // Closing and adding a newline before the Isabelle 'value' calls

        // Indicate that classes have been encoded for this test
        GraalCompilerTest.setClassesEncoded(true);

        return mapping.toString();
    }

    /**
     * Get an Isabelle node array for the specified graph.
     *
     * @param graph The graph to get the node array of
     * @return The node array, or null if it can't be translated
     * @throws IllegalArgumentException if the graph could not be translated.
     */
    public String getNodeArray(StructuredGraph graph) {
        return VeriOptGraphTranslator.writeNodeArray(graph);
//        if (graph.method() == null) {
//            // Can't cache a graph without a method
//            if (VeriOpt.DEBUG) {
//                System.out.println("DEBUG: getNodeArray for graph with no method! " + graph);
//            }
//            return VeriOptGraphTranslator.writeNodeArray(graph);
//        }
//        CacheEntry entry = getCacheEntry(graph.method());
//        if (entry.exception != null) {
//            throw entry.exception;
//        }
//        return entry.nodeArray;
    }

    /**
     * Get every graph being referenced by this method recursively.
     *
     * @param method The method to get the referenced graphs for
     * @return All graphs being referenced by this method and those graphs
     */
    public List<StructuredGraph> getReferencedGraphs(ResolvedJavaMethod method) {
        /* TODO handling empty methods
        if (methodIsEmpty(method)) {
            // Empty methods will not reference any other method.
            return new ArrayList<>();
        }
         */

        // Breadth First Search
        HashSet<CacheEntry> referenceSet = new LinkedHashSet<>();
        Queue<CacheEntry> toSearch = new LinkedList<>();
        toSearch.add(getCacheEntry(method));

        while (!toSearch.isEmpty()) {
            CacheEntry entry = toSearch.poll();

            if (entry.referencedGraphs == null) {
                resolveReferences(entry);
            }

            for (CacheEntry referenced : entry.referencedGraphs) {
                if (referenceSet.add(referenced)) {
                    toSearch.add(referenced);
                }
            }
        }

        // Flatten the CacheEntries into StructuredGraphs
        List<StructuredGraph> referenceList = new ArrayList<>(referenceSet.size());
        for (CacheEntry entry : referenceSet) {
            // if any of the referenced methods have errors, we cannot translate this method.
            if (entry.exception != null) {
                throw entry.exception;
            }
            referenceList.add(entry.graph);
        }

        return referenceList;
    }

    /**
     * Tries to find implementations of all method that might be called by the given CacheEntry method.
     * @param entry
     */
    private void resolveReferences(CacheEntry entry) {
        entry.referencedGraphs = new LinkedHashSet<>();

        if (entry.nodeArray == null || entry.exception != null) {
            // No point resolving references for a graph that doesn't translate
            return;
        }

        // <clinit>
        ResolvedJavaMethod clinit = entry.graph.method().getDeclaringClass().getClassInitializer();
        if (!entry.graph.method().isClassInitializer() && clinit != null) {
            CacheEntry cachedInit = getCacheEntry(clinit);
            entry.referencedGraphs.add(cachedInit);
        }

        // Graphs referenced by nodes in the graph
        for (Node node : entry.graph.getNodes()) {
            ResolvedJavaMethod method = null;

            if (node instanceof CallTargetNode) {
                method = ((CallTargetNode) node).targetMethod();
            }

            if (method != null && !method.isNative()) {
                // Find implementations of this method
                List<ResolvedJavaMethod> implementations = getImplementationsOf(method, entry.graph);
                for (ResolvedJavaMethod implementation : implementations) {
                    CacheEntry cached = getCacheEntry(implementation);
                    entry.referencedGraphs.add(cached);
                }
                CacheEntry cachedMethod = getCacheEntry(method);
                entry.referencedGraphs.add(cachedMethod);
                if (implementations.isEmpty()) {
                    if (Objects.equals(method.getName(), "<init>")) {
                        continue; // don't throw exceptions for empty init methods
                    }
                    cachedMethod.exception = new IllegalArgumentException("no implementations found for "
                     + VeriOpt.formatMethod(method));
                }
            }

            if (node instanceof NewInstanceNode) {
                VeriOptClassHierarchy.processClass(((NewInstanceNode) node).instanceClass());
            }

            if (node instanceof CallTargetNode) {
                VeriOptClassHierarchy.processClass(((CallTargetNode) node).targetMethod().getDeclaringClass());
                // TODO: should we search subclasses too?
            }
        }
    }

    // TODO: this seems naive?  It is only searching for class C where new C appears in this method.
    //  I think it needs to get the class from the method type???  Or the self parameter?
    // But resolveReferences above *does* look at methods from the method type.
    private static List<ResolvedJavaMethod> getImplementationsOf(ResolvedJavaMethod definition, StructuredGraph graph) {
        List<ResolvedJavaMethod> implementations = new ArrayList<>();
        for (Node node : graph.getNodes()) {
            ResolvedJavaType type = null;

            if (node instanceof NewInstanceNode) {
                NewInstanceNode newInstanceNode = (NewInstanceNode) node;
                type = newInstanceNode.instanceClass();
            }

            if (type != null) {
                ResolvedJavaMethod impl = type.findMethod(definition.getName(), definition.getSignature());
                if (impl != null && !impl.isNative()) {
                    implementations.add(impl);
                }
            }
        }

        // also look for the method in the declared class.
        ResolvedJavaType type = definition.getDeclaringClass();
        ResolvedJavaMethod impl = type.findMethod(definition.getName(), definition.getSignature());
        if (impl != null && !impl.isNative()) {
            implementations.add(impl);
        }

        return implementations;
    }

    /**
     * Build an unoptimized StructuredGraph for the given method.
     *
     * @param method The method to build the StructuredGraph for
     * @return A StructuredGraph for the given method.
     */
    private static StructuredGraph buildGraph(ResolvedJavaMethod method) {
        OptionValues options = Graal.getRuntime().getRequiredCapability(OptionValues.class);
        DebugContext debugContext = new DebugContext.Builder(options, Collections.emptyList()).build();
        StructuredGraph.Builder builder = new StructuredGraph.Builder(options, debugContext, StructuredGraph.AllowAssumptions.YES).method(method).compilationId(
                        backend.getCompilationIdentifier(method));
        StructuredGraph graph = builder.build();
        PhaseSuite<HighTierContext> graphBuilderSuite = backend.getSuites().getDefaultGraphBuilderSuite().copy();
        graphBuilderSuite.apply(graph, new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.ALL));
        // Don't optimise the referenced graphs (This can introduce some complicated nodes that
        // could've been avoided)
        // GraalCompiler.emitFrontEnd(providers, backend, graph,
        // backend.getSuites().getDefaultGraphBuilderSuite().copy(), OptimisticOptimizations.ALL,
        // graph.getProfilingInfo(),
        // backend.getSuites().getDefaultSuites(graph.getOptions()).copy());
        return graph;
    }

    /**
     * An object that holds a StructuredGraph, while being able to cache the translated node array
     * and referenced graphs. This object can be used in HashSets with the method as the hash key.
     *
     * The CacheEntry is valid if the nodeArray is non-null and the exception is null;
     *
     * TODO: change the hashcode and equals method to check the graph, not just the method name.
     */
    private static final class CacheEntry {
        private HashSet<CacheEntry> referencedGraphs = null;
        private StructuredGraph graph;
        private String methodName;
        private String nodeArray;
        private RuntimeException exception;  // non-null means this graph cannot be translated to Isabelle.

        private CacheEntry(StructuredGraph graph, String methodName, String nodeArray, RuntimeException exception) {
            this.graph = graph;
            this.methodName = methodName;
            this.nodeArray = nodeArray;
            this.exception = exception;
        }

        private CacheEntry(StructuredGraph graph, String methodName, String nodeArray) {
            this(graph, methodName, nodeArray, null);
        }

            @Override
        public int hashCode() {
            return methodName.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof CacheEntry) {
                return methodName.equals(((CacheEntry) other).methodName);
            }
            return false;
        }
    }

}
