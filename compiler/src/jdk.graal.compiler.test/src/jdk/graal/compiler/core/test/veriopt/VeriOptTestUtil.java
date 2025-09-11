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

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.graal.compiler.core.veriopt.VeriOpt;
import jdk.graal.compiler.core.veriopt.VeriOptGraphTranslator;
import jdk.graal.compiler.core.veriopt.VeriOptValueEncoder;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.nodes.StructuredGraph;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VeriOptTestUtil {
    private static final VeriOptGraphCache veriOptGraphCache = new VeriOptGraphCache(null);

    private StringBuilder stringBuilder = new StringBuilder();

    /**
     * Dump multiple IRGraphs as a single Program.
     *
     * @param graphs The graphs to dump
     * @return A definition of the graphs as a Program in isabelle syntax, with {name} representing
     *         the name of the graph
     */
    public String dumpProgram(StructuredGraph... graphs) {
        stringBuilder.setLength(0);
        stringBuilder.append("definition {name} :: Program where\n");
        stringBuilder.append("  \"{name} = Map.empty (\n");

        // TODO Could pull this out into a function to remove code duplication
        for (StructuredGraph graph : graphs) {
            String graphName = getGraphName(graph);

            String nodeArray = veriOptGraphCache.getNodeArray(graph);
            if (nodeArray != null) {
                stringBuilder.append("  ''").append(graphName).append("'' \\<mapsto> irgraph ");
                stringBuilder.append(nodeArray);
                stringBuilder.append(",\n");
            } else if (graphs[0] == graph) {
                // Error if we can't translate the first graph
                throw new IllegalArgumentException("Could not translate graph for " + VeriOpt.formatMethod(graph.method()));
            }
        }

        // Get graphs for methods which are potentially called.
        Set<StructuredGraph> methodGraphs = getMinimalMethodList(graphs);

        for (StructuredGraph generatedGraph : methodGraphs) {
            String graphName = getGraphName(generatedGraph);

            String nodeArray = veriOptGraphCache.getNodeArray(generatedGraph);
            if (nodeArray != null) {
                stringBuilder.append("  ''").append(graphName).append("'' \\<mapsto> irgraph ");
                stringBuilder.append(nodeArray);
                stringBuilder.append(",\n");
            } else if (graphs[0] == generatedGraph) {
                // Error if we can't translate the first graph
                throw new IllegalArgumentException("Could not translate graph for " + VeriOpt.formatMethod(generatedGraph.method()));
            }
        }

        stringBuilder.setLength(stringBuilder.length() - 2); // remove last comma
        stringBuilder.append("\n  )\"");

        // Append the translation definition
        stringBuilder.append(VeriOptGraphCache.generateJVMClasses(VeriOptGraphTranslator.getClassesToEncode()));

        return stringBuilder.toString();
    }

    /**
     * Helper function to extract the short names (e.g., "add(I)I") of methods. A method's short name is always the
     * last element of its fully-qualified name, separated by a "."
     *
     * @param fullMethodNames a list of fully-qualified method names.
     * */
    private void extractMethodNames(List<String> fullMethodNames) {
        for (int i = 0; i < fullMethodNames.size(); i++) {
            String method = fullMethodNames.get(i);
            String[] methodNameComponents = method.split("\\.");
            fullMethodNames.set(i, methodNameComponents[methodNameComponents.length - 1]);
        }
    }

    /**
     * Returns the set of additional IRGraphs to generate. Methods belonging to instantiated classes with signatures
     * matching methods whose IRGraphs already exist will have their IRGraph added. Any methods invoked within these
     * methods will also have their IRGraph added.
     *
     * @param graphs the original set of methods whose IRGraphs have been generated.
     * @return the set of additional IRGraphs to generate.
     * */
    private Set<StructuredGraph> getMinimalMethodList(StructuredGraph... graphs) {
        // Get the names of all method graphs already generated
        List<String> methodsCallable = new ArrayList<>();
        Arrays.asList(graphs).forEach(graph -> methodsCallable.add(getGraphName(graph)));

        // Get the names of all possible methods
        List<String> methodsAvailable = new ArrayList<>();
        VeriOptGraphTranslator.getCallableMethods().forEach(method -> methodsAvailable.add(VeriOpt.formatMethod(method)));

        // Extract the method's short names
        extractMethodNames(methodsCallable);
        extractMethodNames(methodsAvailable);

        // Add any methods with signatures matching those callable to the list
        List<ResolvedJavaMethod> minimalMethods = new ArrayList<>();
        for (int i = 0; i < methodsAvailable.size(); i++) {
            if (methodsCallable.contains(methodsAvailable.get(i))) {
                minimalMethods.add(new ArrayList<>(VeriOptGraphTranslator.getCallableMethods()).get(i));
            }
        }

        // Generate IRGraphs for the methods and any methods they reference, recursively
        Set<StructuredGraph> methodGraphs = new HashSet<>();
        for (ResolvedJavaMethod method : minimalMethods) {
            /* TODO handling empty methods
            if (VeriOptGraphCache.methodIsEmpty(method)) {
                // The method has no code, and hence no meaningful graph.
                continue;
            }
             */
            StructuredGraph thisGraph = veriOptGraphCache.getGraph(method);
            List<StructuredGraph> referenced = veriOptGraphCache.getReferencedGraphs(method);

            // Populate the set of graphs
            methodGraphs.add(thisGraph);
            methodGraphs.addAll(referenced);
        }

        // Remove duplicates of methods which are referenced more than once
        Set<String> uniqueMethods = new HashSet<>();
        Set<StructuredGraph> minimalMethodGraphs = new HashSet<>();
        for (StructuredGraph graph : methodGraphs) {
            if (uniqueMethods.add(VeriOpt.formatMethod(graph.method()))) {
                // If this is the first instance of this graph, add it to the mapping.
                minimalMethodGraphs.add(graph);
            }
        }

        // Ensure any already-generated graphs aren't duplicated
        Set<String> methodsAlreadyGenerated = new HashSet<>();
        Arrays.asList(graphs).forEach(graph -> methodsAlreadyGenerated.add(getGraphName(graph)));
        minimalMethodGraphs.removeIf(graph -> methodsAlreadyGenerated.contains(getGraphName(graph)));

        return minimalMethodGraphs;
    }

    /**
     * Get a reasonable name for a graph.
     *
     * @param graph The graph to get a name for
     * @return Either Graph.name, StructuredGraph.method().getName(), or null
     */
    public String getGraphName(Graph graph) {
        if (graph.name != null) {
            return graph.name;
        }

        if (graph instanceof StructuredGraph && ((StructuredGraph) graph).method() != null) {
            return VeriOpt.formatMethod(((StructuredGraph) graph).method());
        }

        return null;
    }

    /**
     * Dump a single IRGraph.
     *
     * @param graph The graph to dump
     * @return A definition of the graph as an IRGraph in isabelle syntax, with {name} representing
     *         the name of the graph
     * @throws IllegalArgumentException if graph cannot be translated.
     */
    public String dumpGraph(StructuredGraph graph) {
        stringBuilder.setLength(0);

        stringBuilder.append("definition {name} :: IRGraph where");
        stringBuilder.append(System.lineSeparator());
        stringBuilder.append("  \"{name} = irgraph ");

        String nodeArray = veriOptGraphCache.getNodeArray(graph);  // TODO: PROBLEM: a given method can give different graphs! (some optimization?)
        if (nodeArray == null) {
            throw new IllegalArgumentException("Could not translate graph for " + VeriOpt.formatMethod(graph.method()));
        }
        stringBuilder.append(nodeArray);

        stringBuilder.append("\"");

        // Append the translation definition
        stringBuilder.append(VeriOptGraphCache.generateJVMClasses(VeriOptGraphTranslator.getClassesToEncode()));

        return stringBuilder.toString();
    }

    /**
     * Dump a single IRGraph with the specified name.
     *
     * @param graph The graph to dump
     * @param name The name to give the graph
     * @return A definition of the graph as an IRGraph in isabelle syntax
     * @throws IllegalArgumentException if graph cannot be translated to Isabelle notation.
     */
    public String dumpGraph(StructuredGraph graph, String name) {
        return dumpGraph(graph).replace("{name}", name);
    }

    public String checkResult(Object obj, String checker) {
        Map<String, String> fields = new LinkedHashMap<>();
        StringBuilder check = new StringBuilder();

        if (obj.getClass().isArray()) {
            throw new IllegalArgumentException("unsupported checkResult type: " + obj.getClass().getName());
        }

        getFieldsRecursively(obj, obj.getClass(), fields);

        check.append("True"); // base case for when no checks are needed.
        for (Map.Entry<String, String> field : fields.entrySet()) {
            check.append(" \\<and> ");
            check.append("h_load_field ''");
            check.append(field.getKey());
            check.append("'' x h = ");
            check.append(field.getValue());
        }

        return String.format("fun %s :: \"Value \\<Rightarrow> FieldRefHeap \\<Rightarrow> bool\" where\n"
                + "  \"%s (ObjRef x) h = (%s)\" |\n"
                + "  \"%s _ _ = False\"\n", checker, checker, check.toString(), checker);
    }

    /**
     * Lists all public and private fields for a class and any super classes, and their values for
     * the specified object.
     *
     * @param object The object to retrieve the value for
     * @param clazz The class to retrieve the fields for
     */
    private void getFieldsRecursively(Object object, Class<?> clazz, Map<String, String> fields) {
        if (clazz.getName().equals("java.lang.Object") || clazz.getName().equals("java.lang.Class")) {
            // Ignore these classes (openjdk 8 doesn't like them)
            return;
        }
        // Add this class' fields
        for (Field field : clazz.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                try {
                    field.setAccessible(true); // Let us get the value of private fields
                } catch (RuntimeException ignored) {
                }
                try {
                    Object value = field.get(object);
                    String name = clazz.getName() + "::" + field.getName();
                    if (value == null) {
                        fields.put(name, "(ObjRef None)");
                    } else if (!(value instanceof Number) && !(value instanceof String) && !(value instanceof Boolean)) {
                        getFieldsRecursively(value, value.getClass(), fields);
                    } else {
                        // TODO: should we use the field type here to decide on the stored width?
                        fields.put(name, VeriOptValueEncoder.value(value, false, false));
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
        }

        // Add the super class' fields
        if (clazz.getSuperclass() != null) {
            getFieldsRecursively(object, clazz.getSuperclass(), fields);
        }
    }

    /**
     * Generates the list of input values to pass to the Isabelle test in an Isabelle-friendly format. Reflects the
     * inputs to the unit test.
     *
     * @param args the arguments given to the unit test.
     * @param nonPrimitiveParameterIndexes the indexes of non-primitive inputs in the unit test's parameter list.
     * @return the arguments given to the unit test in an Isabelle-friendly format.
     * */
    public String valueList(Object[] args, List<Integer> nonPrimitiveParameterIndexes) {
        if (args.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < args.length; i++) {
            sb.append(VeriOptValueEncoder.value(args[i], true, nonPrimitiveParameterIndexes.contains(i)));
            sb.append(", ");
        }
        sb.setLength(sb.length() - 2); // remove last separator
        sb.append("]");
        return sb.toString();
    }

}
