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
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;

/**
 * Translates Java values into Isabelle syntax.
 */
public class VeriOptValueEncoder {

    /**
     * Sanitize a Java string so that it can be read by Isabelle.
     *
     *  TODO: handle the case where the string contains two adjacent single quotes.
     *
     * @param str an arbitrary unicode string.
     * @return Isabelle-compatible string with non-ASCII chars turned into octal.
     */
    public static String string2Isabelle(String str) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            int ch = str.codePointAt(i);  // str.getBytes(StandardCharsets.UTF_8)) {
            if (ch == 10) {
                sb.append("\n");
            } else if (ch == 92) {
                sb.append("\\\"");
            } else if (32 <= ch && ch <= 126) {
                assert Character.charCount(ch) == 1;
                sb.appendCodePoint(ch);
            } else {
                throw new IllegalArgumentException("unsupported string literal, code point: " + ch + " in: " + str);
                // TODO: figure out how to translate all unicode chars.
                // sb.append("\\" + Integer.toString(ch & 0xFF, 8));  // TODO pad to 3 digits
            }
        }
        return sb.toString();
    }
/*
    {
        // TODO: move these into unit tests
        assert string2Isabelle("ab c\nd").equals("ab c\nd");
        assert string2Isabelle("More\"+''-\t tricky").equals("More\\\"+\\017\\017-\\011 tricky");
        assert string2Isabelle("\u2A74").equals("\\052\\164");  // Double Colon Equal
    }
*/
    private static String intVal(int bits, long val, boolean paramOrArg) {
        if (paramOrArg && bits < 32) {
            bits = 32;
        }
        // we call this function to truncate to the correct number of bits, so the value is still readable.
        return "(new_int " + bits + " (" + val + "))";
        /*
        switch (bits) {
            case 32: val = val & 0xffffffffL; break;
            case 16: val = val & 0xffffL; break;
            case 8: val = val & 0xffL; break;
            case 1: val = val & 0x01L; break;
            default: throw new IllegalArgumentException("illegal IntVal bits " + bits);
        }
        return "(IntVal " + bits + " (" + val + "))";
        */
    }

    /**
     * Returns a result string of the format (Exception NodeID ''ExceptionType'') for tests which throw exceptions.
     *
     * Currently only handles graphs which generate a single BytecodeExceptionNode.
     *
     * @param exception the exception thrown by the test.
     * @param graph the graph for the test method.
     * @return a result string for the test in an Isabelle-friendly format.
     * */
    public static String exception(Object exception, StructuredGraph graph) {
        String exceptionID = "";
        for (Node node : graph.getNodes()) {
            if (node instanceof BytecodeExceptionNode) {
                exceptionID = node.toString(Verbosity.Id);
                break;
            }
        }

        return "(Exception " + exceptionID + " ''" + exception.getClass().getName() + "'')";
    }

    /**
     * Transforms a constant value into it's Isabelle-friendly representation.
     *
     * @param obj The constant as an object instance. If {@code encodingHeapRef}, this is the object's index in the
     *            Isabelle heap (e.g., "Some 0").
     * @param paramOrArg true if this will be used as a method parameter or result (must be widened to 32 bits).
     * @param encodingHeapRef {@code True} if this value is encoding a reference in the heap, else {@code False}.
     * @return the constant value in it's Isabelle-friendly format.
     */
    public static String value(Object obj, boolean paramOrArg, boolean encodingHeapRef) {
        if (encodingHeapRef) {
            return "(ObjRef " + obj + ")";
        }
        if (obj instanceof Double) {
            Double f = (Double) obj;
            return "(FloatVal 64 (" + f.toString() + "))";
        } else if (obj instanceof Float) {
            Float f = (Float) obj;
            return "(FloatVal 32 (" + f.toString() + "))";
        } else if (obj instanceof Long) {
            Long i = (Long) obj;
            return "(IntVal 64 (" + i.toString() + "))";
        } else if (obj instanceof Integer) {
            return intVal(32, ((Integer) obj).longValue(), paramOrArg);
        } else if (obj instanceof Short) {
            return intVal(16, ((Short) obj).longValue(), paramOrArg);
        } else if (obj instanceof Character) {
            return intVal(16, (long) ((Character) obj).charValue(), paramOrArg);
        } else if (obj instanceof Byte) {
            return intVal(8, ((Byte) obj).longValue(), paramOrArg);
        } else if (obj instanceof Boolean) {
            long val = ((Boolean) obj).booleanValue() ? 1L : 0L;
            return intVal(1, val, paramOrArg);
        } else if (obj instanceof String) {
            String s = (String) obj;
            return "(ObjStr ''" + string2Isabelle(s) + "'')";
        } else if (obj == null) {
            return "(ObjRef None)";
        } else {
            throw new IllegalArgumentException("unsupported value type: " + obj + " (" + obj.getClass().getSimpleName() + ")");
        }
    }
}
