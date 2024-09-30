/*
 * Copyright (c) 2009, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.calc;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.UnaryOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.UnaryOp.Neg;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.spi.StampInverter;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;

//new imports
import java.util.List;
import java.util.Arrays;

/**
 * The {@code NegateNode} node negates its operand.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public class NegateNode extends UnaryArithmeticNode<Neg> implements NarrowableArithmeticNode, StampInverter {

    public static boolean useGenerated;

    static {
        String useGeneratedProp = System.getProperty("useGenerated", "");
        List<String> enabledNodes = Arrays.asList(useGeneratedProp.split(","));
        useGenerated = enabledNodes.contains("NegateNode");
    }


    public static final NodeClass<NegateNode> TYPE = NodeClass.create(NegateNode.class);

    public NegateNode(ValueNode value) {
        this(TYPE, value);
    }

    protected NegateNode(NodeClass<? extends NegateNode> c, ValueNode value) {
        super(c, BinaryArithmeticNode.getArithmeticOpTable(value).getNeg(), value);
    }

    public static ValueNode create(ValueNode value, NodeView view) {
        if (!useGenerated) {
            ValueNode synonym = findSynonym(value, view);
            if (synonym != null) {
                return synonym;
            }
        }
        else if (useGenerated) {
            return canonicalizeGenerated(null, value, view);
        }
        return new NegateNode(value);
    }

    @Override
    protected UnaryOp<Neg> getOp(ArithmeticOpTable table) {
        return table.getNeg();
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode synonym = findSynonym(forValue, getOp(forValue));
        if (synonym != null) {
            return synonym;
        }
        return this;
    }

    protected static ValueNode findSynonym(ValueNode forValue, NodeView view) {
        ArithmeticOpTable.UnaryOp<Neg> negOp = ArithmeticOpTable.forStamp(forValue.stamp(view)).getNeg();

        // Folds constants
        ValueNode synonym = UnaryArithmeticNode.findSynonym(forValue, negOp);
        if (synonym != null) {
            return synonym;
        }
        if (forValue instanceof NegateNode) {
            return ((NegateNode) forValue).getValue();
        }
        if (forValue instanceof SubNode && !(forValue.stamp(view) instanceof FloatStamp)) {
            SubNode sub = (SubNode) forValue;
            return SubNode.create(sub.getY(), sub.getX(), view);
        }
        // e.g. -(x >> 31) => x >>> 31
        if (forValue instanceof RightShiftNode) {
            RightShiftNode shift = (RightShiftNode) forValue;
            Stamp stamp = forValue.stamp(view);
            if (shift.getY().isConstant() && stamp instanceof IntegerStamp) {
                int shiftAmount = shift.getY().asJavaConstant().asInt();
                if (shiftAmount == ((IntegerStamp) stamp).getBits() - 1) {
                    return UnsignedRightShiftNode.create(shift.getX(), shift.getY(), view);
                }
            }
        }
        return null;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitNegate(nodeValueMap.operand(getValue()), false));
    }

    @Override
    public Stamp invertStamp(Stamp outStamp) {
        return getArithmeticOp().foldStamp(outStamp);
    }

    // Below here are the new optimizations methods
    // #Written by Samarth

    public ValueNode canonicalGenerated(CanonicalizerTool tool, ValueNode forValue) { //canonicalGenerated
        ValueNode ret = super.canonical(tool, forValue);
        if (ret != this) {
            return ret;
        }
        return canonicalizeGenerated(this, forValue, NodeView.from(tool)); //canonicalizeGenerated
    }

    private static ValueNode canonicalizeGenerated(NegateNode self, ValueNode x, NodeView view) { //canonicalizeGenerated
        if (x instanceof NegateNode ec) {
            var a = ec.getValue();
            if (a instanceof SubNode ac) {
                var az = ac.getX();
                var b = ac.getY();
                return new SubNode(b, az);
            }
            if (a instanceof NegateNode ac) {
                var az = ac.getValue();
                return az;
            }
        }
        return self != null ? self : new NegateNode(x);
    }

    // combined optimizations for future use if required. will need verification first

    /*protected static ValueNode findSynonym(ValueNode forValue, NodeView view) {
//        if (synonym != null) {
//            return synonym;
//        }
        if (forValue instanceof SubNode sub) {
            return new SubNode(sub.getY(), sub.getX());
        }
        if (forValue instanceof NegateNode neg) {
            return neg.getValue();
        }
        if (forValue instanceof RightShiftNode) {
            RightShiftNode shift = (RightShiftNode) forValue;
            Stamp stamp = forValue.stamp(view);
            if (shift.getY().isConstant() && stamp instanceof IntegerStamp) {
                int shiftAmount = shift.getY().asJavaConstant().asInt();
                if (shiftAmount == ((IntegerStamp) stamp).getBits() - 1) {
                    return UnsignedRightShiftNode.create(shift.getX(), shift.getY(), view);
                }
            }
        }
        ArithmeticOpTable.UnaryOp<Neg> negOp = ArithmeticOpTable.forStamp(forValue.stamp(view)).getNeg();
        ValueNode constantSynonym = UnaryArithmeticNode.findSynonym(forValue, negOp);
        if (constantSynonym != null) {
            return constantSynonym;
        }
        return null;
    }*/

}
