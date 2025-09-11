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
package jdk.graal.compiler.core.veriopt;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodeIterable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

/**
 * Use Java reflection to dynamically generate Isabelle node syntax on the fly.
 */
public class VeriOptDynamicNodeTranslator {
    public static void generateNode(Node node, VeriOptNodeBuilder builder) {
        Class<?> clazz = node.getClass();
        while (Node.class.isAssignableFrom(clazz)) {
            for (Field field : clazz.getDeclaredFields()) {
                // boolean isNode = Node.class.isAssignableFrom(field.getType());
                boolean isNodeList = NodeIterable.class.isAssignableFrom(field.getType());
                if (field.getAnnotation(Node.Input.class) != null || field.getAnnotation(Node.Successor.class) != null) {
                    if (isNodeList) {
                        builder.idList((NodeIterable<?>) getValue(node, field));
                    } else {
                        builder.id((Node) getValue(node, field));
                    }
                } else if (field.getAnnotation(Node.OptionalInput.class) != null) {
                    if (isNodeList) {
                        builder.optIdList((NodeIterable<?>) getValue(node, field));
                    } else {
                        builder.optId((Node) getValue(node, field));
                    }
                }
            }

            clazz = clazz.getSuperclass();
        }
    }

    private static Object getValue(Node node, Field field) {
        try {
            // Method with no prefix
            return node.getClass().getMethod(field.getName()).invoke(node);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        }
        try {
            // Method with a 'get' prefix
            return node.getClass().getMethod("get" + field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1)).invoke(node);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        }
        throw new IllegalArgumentException("Could not find getter for field " + field.getName() + " in class " + node.getClass().getName());
    }
}
