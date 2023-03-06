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
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.options.OptionValues;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * An object representing all the declared static fields and their values for a class.
 */
public class VeriOptFields implements Iterable<Map.Entry<Field, Object>> {

    private final HashMap<Field, Object> fields = new HashMap<>();

    /**
     * Dumps the declared static fields of a class and their values into an object for later use.
     *
     * @param clazz The class declaring the static fields
     * @return The static fields and their values
     */
    public static VeriOptFields getStaticFields(Class<?> clazz) {
        VeriOptFields staticFields = new VeriOptFields();
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                try {
                    field.setAccessible(true); // Let us access private fields
                    staticFields.fields.put(field, field.get(null));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

        return staticFields;
    }

    @Override
    public Iterator<Map.Entry<Field, Object>> iterator() {
        return fields.entrySet().iterator();
    }

    /**
     * Returns {@code true} if there are no static fields.
     *
     * @return {@code true} if there are no static fields
     */
    public boolean isEmpty() {
        return fields.isEmpty();
    }

    /**
     * Filter the static fields down to only those used in LoadFieldNodes within the specified
     * graphs.
     *
     * @param graphs The graphs with the LoadFieldNodes to filter by
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

        Iterator<Field> iterator = fields.keySet().iterator();
        while (iterator.hasNext()) {
            Field field = iterator.next();
            String fieldName = field.getDeclaringClass().getName() + "." + field.getName();
            if (!fieldsAccessed.contains(fieldName)) {
                iterator.remove();
            }
        }
    }

    public StructuredGraph toGraph(OptionValues initialOptions, DebugContext debugContext, MetaAccessProvider metaAccessProvider) {
        StructuredGraph graph = new StructuredGraph.Builder(initialOptions, debugContext).name("").build();

        StartNode startNode = graph.start();
        FrameState frameState = new FrameState(BytecodeFrame.BEFORE_BCI);
        ReturnNode returnNode = new ReturnNode(null);

        graph.add(frameState);

        StoreFieldNode previousStoreFieldNode = null;
        for (Map.Entry<Field, Object> entry : this) {

            JavaConstant constant = JavaConstant.forBoxedPrimitive(entry.getValue());

            if (constant == null) {
                System.out.println("Cannot handle non-primitive field: " + entry.getKey().getName() + " = " + entry.getValue() +
                        (entry.getValue() != null ? " (" + entry.getValue().getClass().getName() + ")" : ""));
                continue;
            }

            ConstantNode constantNode = new ConstantNode(constant, StampFactory.forConstant(constant));
            constantNode = graph.addOrUnique(constantNode);

            StoreFieldNode storeFieldNode = new StoreFieldNode(null, metaAccessProvider.lookupJavaField(entry.getKey()), constantNode);
            storeFieldNode.setStamp(StampFactory.forConstant(constant));
            graph.add(storeFieldNode);

            if (previousStoreFieldNode != null) {
                previousStoreFieldNode.setNext(storeFieldNode);
            }

            if (startNode.next() == null) {
                startNode.setNext(storeFieldNode);
            }

            previousStoreFieldNode = storeFieldNode;
        }

        graph.add(returnNode);

        if (previousStoreFieldNode != null) {
            previousStoreFieldNode.setNext(returnNode);
        } else {
            startNode.setNext(returnNode);
        }

        startNode.setStateAfter(frameState);

        return graph;
    }
}
