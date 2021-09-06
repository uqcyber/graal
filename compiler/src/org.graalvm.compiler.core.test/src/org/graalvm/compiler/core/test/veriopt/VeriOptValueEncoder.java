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
package org.graalvm.compiler.core.test.veriopt;

public class VeriOptValueEncoder {
    public static String value(Object obj) {
        if (obj instanceof Double) {
            Double f = (Double) obj;
            return "(FloatVal 64 (" + f.toString() + "))";
        } else if (obj instanceof Float) {
            Float f = (Float) obj;
            return "(FloatVal 32 (" + f.toString() + "))";
        } else if (obj instanceof Long) {
            Long i = (Long) obj;
            return "(IntVal64 (" + i.toString() + "))";
        } else if (obj instanceof Integer) {
            Integer i = (Integer) obj;
            return "(IntVal32 (" + i.toString() + "))";
        } else if (obj instanceof Short) {
            Short i = (Short) obj;
            return "(IntVal32 (" + i.toString() + "))";
        } else if (obj instanceof Character) {
            Character i = (Character) obj;
            return "(IntVal32 (" + Integer.toString(i) + "))";
        } else if (obj instanceof Byte) {
            Byte i = (Byte) obj;
            return "(IntVal32 (" + i.toString() + "))";
        } else if (obj instanceof Boolean) {
            Boolean b = (Boolean) obj;
            return "(IntVal32 (" + (b ? "1" : "0") + "))";
        } else if (obj instanceof String) {
            String s = (String) obj;
            return "(ObjStr ''" + s + "'')";
        } else if (obj == null) {
            throw new IllegalArgumentException("unsupported value type: " + obj);
        } else {
            throw new IllegalArgumentException("unsupported value type: " + obj + " (" + obj.getClass().getSimpleName() + ")");
        }
    }
}
