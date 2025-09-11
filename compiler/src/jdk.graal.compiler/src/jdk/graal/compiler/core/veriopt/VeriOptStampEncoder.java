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

import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IllegalStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.VoidStamp;

import java.util.HashSet;

/**
 * Encodes GraalVM Stamp objects into the equivalent Isabelle syntax.
 */
public class VeriOptStampEncoder {
    private static HashSet<String> abstractPointerStamps;
    static {
        abstractPointerStamps = new HashSet<>();
        // add just the stamps that we currently handle, with nonNull and alwaysNull fields only.
        abstractPointerStamps.add("KlassPointerStamp");
        abstractPointerStamps.add("MethodCountersPointerStamp");
        abstractPointerStamps.add("MethodPointersStamp");
        abstractPointerStamps.add("RawPointerStamp");
    }

    /**
     * Encode a stamp into an Isabelle representation.
     *
     * @param stamp The stamp to be encoded
     * @return The name of the stamp, followed by any arguments delimited by spaces
     */
    public static String encodeStamp(Stamp stamp) {
        if (stamp instanceof IllegalStamp) {
            return "IllegalStamp";
        } else if (stamp instanceof IntegerStamp) {
            IntegerStamp iStamp = (IntegerStamp) stamp;
            String result;
            if (VeriOpt.ENCODE_INT_MASKS) {
                result = String.format("IntegerStampM %d (%d) (%d) 0x%x 0x%x", iStamp.getBits(),
                        iStamp.lowerBound(), iStamp.upperBound(),
                        iStamp.mustBeSet(), iStamp.mayBeSet());
            } else {
                result = String.format("IntegerStamp %d (%d) (%d)", iStamp.getBits(),
                        iStamp.lowerBound(), iStamp.upperBound());
            }
            return result;
        } else if (stamp instanceof FloatStamp && VeriOpt.ENCODE_FLOAT_STAMPS) {
            FloatStamp floatStamp = (FloatStamp) stamp;
            return "FloatStamp " + floatStamp.getBits() + " (" + floatStamp.lowerBound() + ") (" + floatStamp.upperBound() + ")";
        } else if (stamp instanceof ObjectStamp) {
            ObjectStamp objectStamp = (ObjectStamp) stamp;
            String type = objectStamp.type() == null ? null : objectStamp.type().toClassName();
            return "ObjectStamp ''" + type + "'' " + bool(objectStamp.isExactType()) + " " + bool(objectStamp.nonNull()) + " " + bool(objectStamp.alwaysNull());
        } else if (stamp instanceof AbstractPointerStamp && abstractPointerStamps.contains(stamp.getClass().getSimpleName())) {
            AbstractPointerStamp abstractPointerStamp = (AbstractPointerStamp) stamp;
            return stamp.getClass().getSimpleName() + " " + bool(abstractPointerStamp.nonNull()) + " " + bool(abstractPointerStamp.alwaysNull());
        } else if (stamp instanceof VoidStamp) {
            return "VoidStamp";
        } else {
            throw new IllegalArgumentException("unhandled stamp: " + stamp.getClass().getSimpleName() + ": " + stamp.toString());
        }
    }

    /**
     * Returns the boolean in isabelle syntax.
     *
     * @param bool The boolean to convert to isabelle
     * @return Either True or False
     */
    private static String bool(boolean bool) {
        return bool ? "True" : "False";
    }

}
