/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_4;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp.Mul;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;

/**
 * Multiplication node.
 */
@NodeInfo(shortName = "*", cycles = CYCLES_4, cyclesRationale = "The node cycle estimate is taken from Agner Fog's instruction tables (https://www.agner.org/optimize/instruction_tables.pdf).")
public class MulNode extends BinaryArithmeticNode<Mul> implements NarrowableArithmeticNode, Canonicalizable.BinaryCommutative<ValueNode> {

    public static final NodeClass<MulNode> TYPE = NodeClass.create(MulNode.class);

    public MulNode(ValueNode x, ValueNode y) {
        this(TYPE, x, y);
    }

    protected MulNode(NodeClass<? extends MulNode> c, ValueNode x, ValueNode y) {
        super(c, getArithmeticOpTable(x).getMul(), x, y);
    }

    public static ValueNode create(ValueNode x, ValueNode y, NodeView view) {
        BinaryOp<Mul> op = ArithmeticOpTable.forStamp(x.stamp(view)).getMul();
        Stamp stamp = op.foldStamp(x.stamp(view), y.stamp(view));
        ConstantNode tryConstantFold = tryConstantFold(op, x, y, stamp, view);
        if (tryConstantFold != null) {
            return tryConstantFold;
        }
        return canonical(null, op, stamp, x, y, view);
    }

    @Override
    protected BinaryOp<Mul> getOp(ArithmeticOpTable table) {
        return table.getMul();
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode ret = super.canonical(tool, forX, forY);
        if (ret != this) {
            return ret;
        }

        if (forX.isConstant() && !forY.isConstant()) {
            // we try to swap and canonicalize
            ValueNode improvement = canonical(tool, forY, forX);
            if (improvement != this) {
                return improvement;
            }
            // if this fails we only swap
            return new MulNode(forY, forX);
        }

        // convert "(-a)*(-b)" into "a*b"
        if (forX instanceof NegateNode && forY instanceof NegateNode) {
            // veriopt: EliminateRedundantNegative: (-x) * (-y) |-> x * y
            return new MulNode(((NegateNode) forX).getValue(), ((NegateNode) forY).getValue()).maybeCommuteInputs();
        }

        BinaryOp<Mul> op = getOp(forX, forY);
        NodeView view = NodeView.from(tool);
        return canonical(this, op, stamp(view), forX, forY, view);
    }

    private static ValueNode canonical(MulNode self, BinaryOp<Mul> op, Stamp stamp, ValueNode forX, ValueNode forY, NodeView view) {
        if (forY.isConstant()) {
            Constant c = forY.asConstant();
            if (op.isNeutral(c)) {
                // veriopt: MulNeutral2: x * const(1) |-> x
                return forX;
            }

            if (op.isAssociative()) {
                /*
                 * Canonicalize expressions like "(a * 2) * 4" => "(a * 8)". Account for the
                 * possibility that the inner multiply is represented as a shift.
                 */
                ValueNode assocX = forX;
                MulNode assocSelf = self;
                if (forX instanceof LeftShiftNode leftShift && leftShift.getY().isJavaConstant()) {
                    ValueNode xAsMultiply = leftShift.getEquivalentMulNode();
                    if (xAsMultiply != null) {
                        assocX = xAsMultiply;
                        assocSelf = null;  // must build a new node for association
                    }
                }
                MulNode assocNode = assocSelf != null ? assocSelf : (MulNode) new MulNode(assocX, forY).maybeCommuteInputs();
                ValueNode reassociated = reassociateMatchedValues(assocNode, ValueNode.isConstantPredicate(), assocX, forY, view);
                if (reassociated != self) {
                    return reassociated;
                }
            }
            if (c instanceof PrimitiveConstant && ((PrimitiveConstant) c).getJavaKind().isNumericInteger()) {
                long i = ((PrimitiveConstant) c).asLong();
                ValueNode result = canonical(stamp, forX, i, view);
                if (result != null) {
                    return result;
                }
            }
        }
        return self != null ? self : new MulNode(forX, forY).maybeCommuteInputs();
    }

    public static ValueNode canonical(Stamp stamp, ValueNode forX, long i, NodeView view) {
        if (i == 0) {
            // veriopt: MulEliminator: x * const(0) |-> const(0)
            return ConstantNode.forIntegerStamp(stamp, 0);
        } else if (i == 1) {
            // veriopt: MulNeutral: x * const(1) |-> x
            return forX;
        } else if (i == -1) {
            // veriopt: MulNegate: x * const(-1) |-> -x
            return NegateNode.create(forX, view);
        } else if (i > 0) {
            if (CodeUtil.isPowerOf2(i)) {
                // veriopt: MulPower2: x * const(2^j) |-> x << const(j) when (j >= 0)
                return new LeftShiftNode(forX, ConstantNode.forInt(CodeUtil.log2(i)));
            } else if (CodeUtil.isPowerOf2(i - 1)) {
                // veriopt: MulPower2Add1: x * const((2^j) + 1) |-> x << const(j) + x
                return AddNode.create(new LeftShiftNode(forX, ConstantNode.forInt(CodeUtil.log2(i - 1))), forX, view);
            } else if (CodeUtil.isPowerOf2(i + 1)) {
                // veriopt: MulPower2Sub1: x * const((2^j) - 1) |-> x << const(j) - x
                return SubNode.create(new LeftShiftNode(forX, ConstantNode.forInt(CodeUtil.log2(i + 1))), forX, view);
            } else {
                // veriopt-definition: constant_condition: (is_ConstantExpr c && c > 0 && c != 1 && ~is_Power2(c) &&
                //                                          ~is_Power2(c + 1) && ~is_Power2(c - 1))

                // veriopt-definition: highestBitValue: highestOneBit_Long c
                int bitCount = Long.bitCount(i);
                long highestBitValue = Long.highestOneBit(i);
                if (bitCount == 2) {
                    // e.g., 0b1000_0010
                    long lowerBitValue = i - highestBitValue;
                    assert highestBitValue > 0 && lowerBitValue > 0 : Assertions.errorMessageContext("stamp", stamp, "forX", forX, "i", i, "highestBitVal", highestBitValue, "lowerBitVal",
                                    lowerBitValue);
                    // The lower bit cannot be one because that case is handled by i - 1 above.
                    assert lowerBitValue > 1 : Assertions.errorMessageContext("stamp", stamp, "forX", forX, "i", i, "lowerBitVal", lowerBitValue);
                    ValueNode left = new LeftShiftNode(forX, ConstantNode.forInt(CodeUtil.log2(highestBitValue)));
                    ValueNode right = new LeftShiftNode(forX, ConstantNode.forInt(CodeUtil.log2(lowerBitValue)));

                    // veriopt-definition: lowerBitValue: c - highestBitValue c

                    // veriopt: MulPower2AddPower2: x * c |-> (x << const(log2 (highestBitValue))) + (x << const(log2 (lowerBitValue)))
                    //          when (constant_condition && bitCount c = 2 && lowerBitValue > 1)
                    return AddNode.create(left, right, view);
                } else {
                    // e.g., 0b1111_1100
                    int shiftToRoundUpToPowerOf2 = CodeUtil.log2(highestBitValue) + 1;
                    long subValue = (1L << shiftToRoundUpToPowerOf2) - i;
                    if (CodeUtil.isPowerOf2(subValue) && shiftToRoundUpToPowerOf2 < ((IntegerStamp) stamp).getBits()) {
                        ValueNode left = new LeftShiftNode(forX, ConstantNode.forInt(shiftToRoundUpToPowerOf2));
                        // For Long.MAX_VALUE, i + 1 overflows, so this valid subtraction by one is
                        // not handled by the preceding i + 1 check.
                        ValueNode right = subValue == 1 ? forX : new LeftShiftNode(forX, ConstantNode.forInt(CodeUtil.log2(subValue)));

                        // veriopt-definition: shiftPow2: (log2 (highestBitValue)) + 1
                        // veriopt-definition: subVal: (1 << shiftPow2) - c

                        // veriopt: MulPower2SubPower2:   x * c |-> (x << const(shiftPow2)) - (x << const(log2 (subVal)))
                        //          when (constant_condition && bitCount c != 2 && is_Power2(subVal) && subVal != 1 &&
                        //                stamp_expr (x * c) = IntegerStamp b lo hi && shiftPow2 < b)

                        // veriopt: MulPower2Sub1_MaxVal: x * c |-> (x << const(shiftPow2)) - x
                        //          when (constant_condition && bitCount c != 2 && is_Power2(subVal) && subVal == 1 &&
                        //                stamp_expr (x * c) = IntegerStamp b lo hi && shiftPow2 < b)
                        return SubNode.create(left, right, view);
                    }
                }
            }
        } else if (i < 0) {
            if (stamp instanceof IntegerStamp integerStamp && i == CodeUtil.minValue(integerStamp.getBits())) {
                /*
                 * The min value is negative, but for multiplication it behaves like the largest
                 * unsigned power of 2. So unlike the case below, we do not need a negation.
                 */
                return LeftShiftNode.create(forX, ConstantNode.forInt(integerStamp.getBits() - 1), view);
            }
            if (CodeUtil.isPowerOf2(-i)) {
                // veriopt: MulNegativeConstShift: x * const(-(2^j)) |-> -(x << const(j))
                return NegateNode.create(LeftShiftNode.create(forX, ConstantNode.forInt(CodeUtil.log2(-i)), view), view);
            }
        }
        return null;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        Value op1 = nodeValueMap.operand(getX());
        Value op2 = nodeValueMap.operand(getY());
        if (shouldSwapInputs(nodeValueMap)) {
            Value tmp = op1;
            op1 = op2;
            op2 = tmp;
        }
        nodeValueMap.setResult(this, gen.emitMul(op1, op2, false));
    }

    protected boolean isExact() {
        return false;
    }
}
