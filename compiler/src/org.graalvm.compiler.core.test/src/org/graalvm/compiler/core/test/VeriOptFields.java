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
package org.graalvm.compiler.core.test;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.veriopt.VeriOpt;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.options.OptionValues;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Stores the default values for all the fields for a class.
 */
public class VeriOptFields {

    // Store the static fields and their default values
    private static HashMap<Field, Object> staticFields = new HashMap<>();

    // Store the dynamic fields and their default values
    private static HashMap<Field, Object> dynamicFields = new HashMap<>();

    // Store any NewInstanceNodes and the dynamic fields of the class the node instantiates.
    private static HashMap<NewInstanceNode, List<Field>> dynamicFieldReferences = new HashMap<>();

    // Retrieve default values for types
    static boolean DEFAULT_BOOL;
    static byte DEFAULT_BYTE;
    static char DEFAULT_CHAR;
    static double DEFAULT_DOUBLE;
    static float DEFAULT_FLOAT;
    static int DEFAULT_INT;
    static long DEFAULT_LONG;
    static short DEFAULT_SHORT;
    static Object DEFAULT_OBJECT;

    // Populate defaultValues with the default values for the types.
    private static Map<Class<?>, Object> defaultValues = new HashMap<>();
    static {
        defaultValues.put(boolean.class, DEFAULT_BOOL);
        defaultValues.put(byte.class,    DEFAULT_BYTE);
        defaultValues.put(char.class,    DEFAULT_CHAR);
        defaultValues.put(double.class,  DEFAULT_DOUBLE);
        defaultValues.put(float.class,   DEFAULT_FLOAT);
        defaultValues.put(int.class,     DEFAULT_INT);
        defaultValues.put(long.class,    DEFAULT_LONG);
        defaultValues.put(short.class,   DEFAULT_SHORT);
    }

    /**
     * Indicates whether a field is static or dynamic based on its modifiers.
     * */
    private enum FieldType {
        STATIC, DYNAMIC
    }

    /**
     * Stores the static fields of a {@code clazz}, and any classes that they immediately declare, and their default
     * values.
     *
     * @param clazz the class whose own and immediately-declared classes fields are being stored.
     * */
    public void getClassFields(Class<?> clazz) {
        // Retrieve the static fields
        getFields(clazz, FieldType.STATIC);

        // Instantiate fields for immediately declared classes
        for (Class<?> inner : clazz.getDeclaredClasses()) {
            getFields(inner, FieldType.STATIC);
        }
    }

    /**
     * Stores the fields of a class and their default values into the appropriate mapping (staticFields or
     * dynamicFields).
     *
     * @param clazz the class declaring the fields.
     * @param type the field type being stored (either static or dynamic).
     */
    public static void getFields(Class<?> clazz, FieldType type) {
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true); // Let us access private fields
            Object defaultValue = defaultValues.getOrDefault(field.getType(), DEFAULT_OBJECT);

            if (Modifier.isStatic(field.getModifiers()) && type == FieldType.STATIC) {
                staticFields.put(field, defaultValue);
            } else if (!(Modifier.isStatic(field.getModifiers())) && type == FieldType.DYNAMIC) {
                dynamicFields.put(field, defaultValue);
            }
        }
    }

    /**
     * // TODO change this so that a unique function is called to store the fields, instead of altering the graph?
     *
     * Instantiates a class' dynamic fields to their default values by altering the graph after the NewInstanceNode
     * that instantiated the class.
     *
     * @param graph the original graph containing the NewInstanceNodes.
     * */
    private void storeDynamicFields(StructuredGraph graph, MetaAccessProvider metaAccessProvider) {
        for (NewInstanceNode startNode : dynamicFieldReferences.keySet()) {
            FixedNode endNode = startNode.next();

            // Set the fields to their default values
            StoreFieldNode previousStoreFieldNode = null;
            previousStoreFieldNode = storeFieldsGraph(graph, startNode, dynamicFieldReferences.get(startNode), dynamicFields,
                    previousStoreFieldNode, metaAccessProvider, startNode, endNode);

            if (previousStoreFieldNode != null) {
                previousStoreFieldNode.setNext(endNode);
            } else {
                startNode.setNext(endNode);
            }
        }

        // Once this graph is done, restore the dynamicFields for the next graph
        dynamicFields.clear();
        dynamicFieldReferences.clear();
    }

    /**
     * Stores the dynamic fields & their default values for the classes listed in {@code classes}. Searches through the
     * program and stores the NewInstanceNode that the class was instantiated at, and calls a method to update the graph
     * to set the dynamic fields to their default values.
     *
     * @param program the program containing the graphs that may be altered
     * @param classes the classes whose dynamic fields will be instantiated to their default values.
     * */
    public void instantiateDynamicFields(List<StructuredGraph> program, MetaAccessProvider metaAccessProvider,
                                         HashMap<String, Class<?>> classes) {
        for (StructuredGraph graph : program) {
            for (Node node : graph.getNodes()) {
                if (node instanceof NewInstanceNode) {
                    // Retrieve the name of the class instantiated
                    NewInstanceNode newInstanceNode = (NewInstanceNode) node;
                    String newClassName = newInstanceNode.instanceClass().toClassName();

                    if (classes.get(newClassName) == null) {
                        // Class being instantiated isn't in list of classes to instantiate fields for.
                        System.out.println("MESSAGE: NewInstanceNode is instantiating a class which isn't in the classes list, skipping");
                        continue;
                    }
                    // Retrieve and store the class & their dynamic fields' default values
                    Class<?> newClass = classes.get(newClassName);
                    getFields(newClass, FieldType.DYNAMIC);

                    // Ensure the graph update doesn't attempt to instantiate static fields
                    List<Field> dynamicFieldsForClass = new ArrayList<>();
                    for (Field field : newClass.getDeclaredFields()) {
                        if (!Modifier.isStatic(field.getModifiers())) {
                            dynamicFieldsForClass.add(field);
                        }
                    }

                    // Store the NewInstanceNode and the class' dynamic fields.
                    dynamicFieldReferences.put(newInstanceNode, dynamicFieldsForClass);
                }
            }

            if (!(dynamicFields.isEmpty() || dynamicFieldReferences.isEmpty())) {
                // Update the graph to instantiate the dynamic fields of the class created by the NewInstanceNode
                storeDynamicFields(graph, metaAccessProvider);
            }
        }
    }

    /**
     * Returns {@code true} if there are no static fields.
     *
     * @return {@code true} if there are no static fields
     */
    public boolean isEmpty() {
        return staticFields.isEmpty();
    }

    /**
     * Returns the stored static fields and their default values.
     *
     * @return the static fields and their corresponding default values.
     * */
    public HashMap<Field, Object> getContent() {
        return staticFields;
    }

    /**
     * Filter the static fields down to only those used in LoadFieldNodes within the specified graphs.
     *
     * @param graphs The graphs with the LoadFieldNodes to filter by.
     */
    public void filterFields(Graph... graphs) {
        HashSet<String> fieldsAccessed = new HashSet<>();

        for (Graph graph : graphs) {
            for (Node node : graph.getNodes()) {
                if (node instanceof LoadFieldNode) {
                    LoadFieldNode loadFieldNode = (LoadFieldNode) node;
                    String fieldName = loadFieldNode.field().getDeclaringClass().toClassName() + "." + loadFieldNode.field().getName();
                    fieldsAccessed.add(fieldName);
                }
            }
        }

        Iterator<Field> iterator = staticFields.keySet().iterator();
        while (iterator.hasNext()) {
            Field field = iterator.next();
            String fieldName = field.getDeclaringClass().getName() + "." + field.getName();
            if (!fieldsAccessed.contains(fieldName)) {
                iterator.remove();
            }
        }
    }

    /**
     * Creates a graph segment which stores a sequence of values into their corresponding field, extending the
     * pre-existing {@code graph}.
     *
     * @param graph the current graph.
     * @param startNode the node which this graph segment will begin from.
     * @param fields the fields which will be instantiated.
     * @param fieldValues a mapping from fields to the values they will be instantiated to.
     * @param previousStoreFieldNode the most recent StoreFieldNode.
     * @param storeFieldRef the reference that the StoreFieldNode for a field will point to. For static fields, this is
     *                      null. For dynamic fields, it's a reference to the instance of the class that the field
     *                      belongs to.
     * @param currentEndNode the node which succeeds the startNode currently. For a new graph creation, this is null.
     *                       If a pre-existing graph is being re-structured, this is startNode.next()
     * */
    private <T extends FixedWithNextNode> StoreFieldNode
        storeFieldsGraph(StructuredGraph graph, T startNode, List<Field> fields, HashMap<Field, Object> fieldValues,
                         StoreFieldNode previousStoreFieldNode, MetaAccessProvider metaAccessProvider,
                         ValueNode storeFieldRef, FixedNode currentEndNode) {

        for (Field field : fields) {

            JavaConstant constant = JavaConstant.forBoxedPrimitive(fieldValues.get(field));

            if (constant == null) {
                continue;
            }

            ConstantNode constantNode = ConstantNode.forConstant(constant, metaAccessProvider, graph);
            constantNode = graph.addOrUnique(constantNode);

            StoreFieldNode storeFieldNode = new StoreFieldNode(storeFieldRef, metaAccessProvider.lookupJavaField(field), constantNode);
            storeFieldNode.setStamp(StampFactory.forConstant(constant));
            graph.add(storeFieldNode);

            if (previousStoreFieldNode != null) {
                previousStoreFieldNode.setNext(storeFieldNode);
            }

            if (startNode.next() == currentEndNode) {
                startNode.setNext(storeFieldNode);
            }

            previousStoreFieldNode = storeFieldNode;
        }

        return previousStoreFieldNode;
    }

    /**
     * Generates and returns the graph which instantiates the stored fields to their default values.
     *
     * The graph may also contain a call to a method (clinit) to overwrite the default values of fields to their
     * instantiated values, if they are instantiated in the class.
     *
     * @param fields the fields whose default values are stored by this class.
     * @param invokingAfter the method being invoked after the instantiation of fields to their default values (ideally
     *                      clinit to overwrite any instantiated fields to their true values).
     * @return the graph performing the instantiation of the stored fields to their default values, and a call to the
     *         {@code invokingAfter} method if it isn't null.
     * */
    public StructuredGraph toGraph(OptionValues initialOptions, DebugContext debugContext,
                                   MetaAccessProvider metaAccessProvider, HashMap<Field, Object> fields,
                                   ResolvedJavaMethod invokingAfter) {
        // Initial setup
        StructuredGraph graph = new StructuredGraph.Builder(initialOptions, debugContext).name("").build();

        StartNode startNode = graph.start();

        FrameState frameState = new FrameState(BytecodeFrame.BEFORE_BCI);
        graph.add(frameState);
        startNode.setStateAfter(frameState);

        // Set the fields to their default values
        StoreFieldNode previousStoreFieldNode = null;
        previousStoreFieldNode = storeFieldsGraph(graph, startNode, new ArrayList<>(fields.keySet()), fields, previousStoreFieldNode,
                metaAccessProvider, null, null);

        // Check if clinit needs to overwrite any values
        if (invokingAfter != null) {
            /* The class contains instantiated fields */
            // Select the last node of the current graph, depending on whether there were fields stored.
            Node newStartNode = (previousStoreFieldNode != null) ? previousStoreFieldNode : startNode;

            // Extend the current graph to call clinit, and return the entire graph.
            return VeriOpt.invokeGraph(invokingAfter, graph, newStartNode);
        }

        /* The class doesn't contain instantiated fields */
        // Finalise this graph and return it.
        ReturnNode returnNode = new ReturnNode(null);
        graph.add(returnNode);

        if (previousStoreFieldNode != null) {
            previousStoreFieldNode.setNext(returnNode);
        } else {
            startNode.setNext(returnNode);
        }

        return graph;
    }
}
