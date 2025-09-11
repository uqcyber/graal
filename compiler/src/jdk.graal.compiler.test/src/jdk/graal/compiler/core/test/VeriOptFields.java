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
package jdk.graal.compiler.core.test;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.veriopt.VeriOpt;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.options.OptionValues;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manages all the fields, their values and their instantiation in the translated graph, for the test class and any
 * classes it declares.
 */
public class VeriOptFields {

    // Stores the static fields and their default values
    private static HashMap<Field, Object> staticFields = new HashMap<>();

    // Stores the dynamic fields and their default or actual values
    private HashMap<Field, Object> dynamicFields = new HashMap<>();

    // Stores any NewInstanceNodes and the dynamic fields of the class the node instantiates.
    private HashMap<NewInstanceNode, List<Field>> dynamicFieldReferences = new HashMap<>();

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
     * Stores the static fields and their default values for the {@code classes} given.
     *
     * @param classes the classes whose fields are being stored.
     * */
    public void getStaticClassFields(List<Class<?>> classes) {
        // Store static fields for classes
        for (Class<?> clazz : classes) {
            getFields(clazz, FieldType.STATIC, null);
        }
    }

    /**
     * Stores a class fields and their values into the appropriate mapping (staticFields or dynamicFields).
     *
     * @param clazz the class declaring the fields.
     * @param type the field type being stored (either static or dynamic).
     * @param classInstance the object instance for which the field values are being stored (value ignored if
     *                      {@code type} is static). If this is {@code null}, fields are set to their default values.
     */
    public void getFields(Class<?> clazz, FieldType type, Object classInstance) {
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true); // Let us access private fields
            Object defaultValue = defaultValues.getOrDefault(field.getType(), DEFAULT_OBJECT);

            if (Modifier.isStatic(field.getModifiers()) && type == FieldType.STATIC) {
                staticFields.put(field, defaultValue);
            } else if (!(Modifier.isStatic(field.getModifiers())) && type == FieldType.DYNAMIC) {

                // If we have an object instance, we can access the fields value, otherwise store their default.
                if (classInstance != null) {
                    try {
                        Object fieldValue = field.get(classInstance);
                        dynamicFields.put(field, fieldValue);
                    } catch (IllegalAccessException e) {
                        System.out.println("IllegalAccessException. Cannot access field value. MESSAGE = " + e.getMessage());
                    }
                } else {
                    dynamicFields.put(field, defaultValue);
                }
            }
        }
    }

    /**
     * // TODO change this so that a unique function is called to store the fields, instead of altering the graph?
     *
     * Instantiates an objects dynamic fields by altering the graph after the objects' NewInstanceNode.
     *
     * @param graph the original graph containing the NewInstanceNodes.
     * @param storingDefaults {@code true} if fields are being instantiated to their default values, else {@code false}.
     * @return the end-point of the new segment added to the {@code graph}.
     * */
    private FixedWithNextNode storeDynamicFields(StructuredGraph graph, MetaAccessProvider metaAccessProvider,
                                                 boolean storingDefaults) {
        FixedWithNextNode graphEndNode = null;
        for (NewInstanceNode startNode : dynamicFieldReferences.keySet()) {
            FixedNode endNode = startNode.next();

            // Set the fields values
            FixedWithNextNode previousStoreFieldNode = null;
            previousStoreFieldNode = storeFieldsGraph(graph, startNode, dynamicFieldReferences.get(startNode),
                    dynamicFields, previousStoreFieldNode, metaAccessProvider, startNode, endNode, storingDefaults);

            if (previousStoreFieldNode != null) {
                previousStoreFieldNode.setNext(endNode);
                graphEndNode = previousStoreFieldNode;
            } else {
                startNode.setNext(endNode);
                graphEndNode = startNode;
            }
        }

        // Once this graph is done, restore the dynamic data structures for the next graph.
        clearDynamic();

        // Return the end-point of the graph. Will be null if nothing was stored.
        return graphEndNode;
    }

    /**
     * Stores and instantiates the dynamic fields & their default values for the classes listed in {@code classes}.
     *
     * Searches through every {@code graph} in the {@code program} and stores the NewInstanceNodes and the
     * corresponding class' dynamic fields. Calls a method to update the {@code graph} to set the fields to their
     * default values.
     *
     * @param program the program containing the graphs that may be altered.
     * @param classes the classes whose dynamic fields will be instantiated to their default values.
     * */
    public void instantiateDynamicFields(List<StructuredGraph> program, MetaAccessProvider metaAccessProvider,
                                         HashMap<String, Class<?>> classes) {
        for (StructuredGraph graph : program) {
            if (Objects.equals(graph.name, "")) {
                // Ignore the empty setup graph
                continue;
            }

            for (Node node : graph.getNodes()) {
                if (node instanceof NewInstanceNode) {
                    // Retrieve the name of the class instantiated
                    NewInstanceNode newInstanceNode = (NewInstanceNode) node;
                    String newClassName = newInstanceNode.instanceClass().toClassName();

                    if (classes.get(newClassName) == null) {
                        // Class being instantiated isn't in list of classes to instantiate fields for.
                        continue;
                    }

                    // Retrieve and store the class & their dynamic fields default values
                    Class<?> newClass = classes.get(newClassName);
                    prepareDynamicFields(newClass, newInstanceNode, null);
                }
            }

            if (!(dynamicFields.isEmpty() || dynamicFieldReferences.isEmpty())) {
                // Update the graph to instantiate the dynamic fields of the class created by the NewInstanceNode
                storeDynamicFields(graph, metaAccessProvider, true);
            }
        }
    }

    /**
     * Clears the setup for dynamic fields by resetting {@link #dynamicFieldReferences} and {@link #dynamicFields}.
     * */
    public void clearDynamic() {
        dynamicFieldReferences.clear();
        dynamicFields.clear();
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
     * @param storingDefaults {@code true} if the default values of fields are being stored, else {@code false}.
     * @return the final StoreFieldNode in the graph segment generated. If {@code fields} was empty, this is the
     *         original {@code previousStoreFieldNode} given.
     * */
    private <T extends FixedWithNextNode> FixedWithNextNode
        storeFieldsGraph(StructuredGraph graph, T startNode, List<Field> fields, HashMap<Field, Object> fieldValues,
                         FixedWithNextNode previousStoreFieldNode, MetaAccessProvider metaAccessProvider,
                         ValueNode storeFieldRef, FixedNode currentEndNode, boolean storingDefaults) {

        for (Field field : fields) {

            JavaConstant constant = JavaConstant.forBoxedPrimitive(fieldValues.get(field));

            if (constant == null) {
                // Constant is non-primitive
                constant = JavaConstant.NULL_POINTER;
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
     * Stores the dynamic fields & values, and corresponding NewInstanceNode for the {@code classInstance}. This occurs
     * prior to the graph being updated to store the fields values, by storeDynamicFields.
     *
     * @see #storeDynamicFields(StructuredGraph, MetaAccessProvider, boolean)
     * @param newClass the class whose dynamic fields are being stored.
     * @param node the corresponding NewInstanceNode for the {@code classInstance}.
     * @param classInstance the classInstance whose dynamic fields are being stored. This can be null.
     * */
    private void prepareDynamicFields(Class<?> newClass, NewInstanceNode node, Object classInstance) {
        // Retrieve the values of the class' dynamic fields.
        getFields(newClass, FieldType.DYNAMIC, classInstance);

        // Ensure the graph update doesn't attempt to instantiate static fields
        List<Field> dynamicFieldsForClass = new ArrayList<>();
        for (Field field : newClass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                dynamicFieldsForClass.add(field);
            }
        }

        // Store the NewInstanceNode and the class' dynamic fields.
        dynamicFieldReferences.put(node, dynamicFieldsForClass);
    }

    /**
     * Generates and returns a setup graph which may:
     *  (1) Instantiate class fields to their default or actual values.
     *  (2) Call the clinit method to overwrite default field values, if they are instantiated in the class.
     *  (3) Create instances of any non-primitive arguments to the {@code testMethod}, including the test class itself
     *      (for dynamic tests), and instantiate their fields.
     *
     * @param fields the static fields whose default values are stored by this class.
     * @param invokingAfter the method being invoked after the fields are instantiated (ideally clinit to overwrite any
     *                      instantiated fields to their true values).
     * @param testMethod the test method for which this initial setup graph is being generated.
     * @param nonPrimitiveParameters a mapping from the class names to classes of the non-primitive parameters of the
     *                               {@code testMethod}.
     * @param args the arguments passed to the {@code testMethod}.
     * @param testClass the class declaring the {@code testMethod}.
     * @return a setup graph performing the aforementioned actions for the test being run.
     * */
    public StructuredGraph toGraph(OptionValues initialOptions, DebugContext debugContext,
                                   MetaAccessProvider metaAccessProvider, HashMap<Field, Object> fields,
                                   ResolvedJavaMethod invokingAfter, ResolvedJavaMethod testMethod,
                                   HashMap<String, Class<?>> nonPrimitiveParameters, Object[] args, Class<?> testClass) {
        // Initial setup
        StructuredGraph graph = new StructuredGraph.Builder(initialOptions, debugContext).name("").build();
        StartNode startNode = graph.start();
        FrameState frameState = new FrameState(BytecodeFrame.BEFORE_BCI);
        graph.add(frameState);
        startNode.setStateAfter(frameState);

        // The final node of the initial part of the graph.
        FixedWithNextNode tailNode = startNode;

        if (!testMethod.isStatic()) {
            // Allocate the test, so that it may be accessed in the heap.
            NewInstanceNode test = new NewInstanceNode(testMethod.getDeclaringClass(), true);
            graph.add(test);
            tailNode.setNext(test);
            tailNode = test;

            // Store the test class' dynamic fields
            prepareDynamicFields(testClass, test, null);
            FixedWithNextNode newTail = storeDynamicFields(graph, metaAccessProvider, false);
            tailNode = (newTail == null) ? tailNode : newTail;
        }

        if (!nonPrimitiveParameters.isEmpty()) {
            for (int i = 0; i < testMethod.toParameterTypes().length; i++) {
                JavaType currentParameter = testMethod.toParameterTypes()[i];
                ResolvedJavaType resolved = currentParameter.resolve(testMethod.getDeclaringClass());

                if (nonPrimitiveParameters.containsKey(resolved.toClassName())) {
                    // Allocate any non-primitive arguments, so that they can be accessed in the heap.
                    NewInstanceNode parameterNode = new NewInstanceNode(resolved, true);
                    graph.add(parameterNode);
                    tailNode.setNext(parameterNode);
                    tailNode = parameterNode;

                    // Store the dynamic fields
                    Class<?> newClass = nonPrimitiveParameters.get(resolved.toClassName());
                    prepareDynamicFields(newClass, parameterNode, args[i]);
                }
            }
            // Update the graph to store the non-primitive parameters dynamic fields
            FixedWithNextNode newTail = storeDynamicFields(graph, metaAccessProvider, false);
            tailNode = (newTail == null) ? tailNode : newTail;
        }

        // Set the static fields of the test class declaration list to their default values
        FixedWithNextNode previousStoreFieldNode = tailNode;
        previousStoreFieldNode = storeFieldsGraph(graph, tailNode, new ArrayList<>(fields.keySet()), fields,
                previousStoreFieldNode, metaAccessProvider, null, null, true);

        // Check if clinit needs to overwrite any values
        if (invokingAfter != null) {
            // Extend the current graph to call clinit, and return the entire graph.
            return VeriOpt.invokeGraph(invokingAfter, graph, previousStoreFieldNode);
        }

        // Finalise this graph and return it.
        ReturnNode returnNode = new ReturnNode(null);
        graph.add(returnNode);

        if (previousStoreFieldNode != null) {
            previousStoreFieldNode.setNext(returnNode);
        } else {
            tailNode.setNext(returnNode);
        }

        return graph;
    }
}
