/*
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.calc;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;
import static org.graalvm.compiler.nodes.calc.CompareNode.createCompareNode;

import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool.RoundingMode;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNegationNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * The {@code ConditionalNode} class represents a comparison that yields one of two (eagerly
 * evaluated) values.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_2)
public final class ConditionalNode extends FloatingNode implements Canonicalizable, LIRLowerable {

    public static final NodeClass<ConditionalNode> TYPE = NodeClass.create(ConditionalNode.class);
    @Input(InputType.Condition) LogicNode condition;
    @Input(InputType.Value) ValueNode trueValue;
    @Input(InputType.Value) ValueNode falseValue;

    public LogicNode condition() {
        return condition;
    }

    public ConditionalNode(LogicNode condition) {
        this(condition, ConstantNode.forInt(1, condition.graph()), ConstantNode.forInt(0, condition.graph()));
    }

    public ConditionalNode(LogicNode condition, ValueNode trueValue, ValueNode falseValue) {
        super(TYPE, combineStamps(condition, trueValue, falseValue, NodeView.DEFAULT));
        assert trueValue.stamp(NodeView.DEFAULT).isCompatible(falseValue.stamp(NodeView.DEFAULT));
        this.condition = condition;
        this.trueValue = trueValue;
        this.falseValue = falseValue;
    }

    public static ValueNode create(LogicNode condition, NodeView view) {
        return create(condition, ConstantNode.forInt(1, condition.graph()), ConstantNode.forInt(0, condition.graph()), view);
    }

    public static ValueNode create(LogicNode condition, ValueNode trueValue, ValueNode falseValue, NodeView view) {
        ValueNode synonym = findSynonym(condition, trueValue, falseValue, view);
        if (synonym != null) {
            return synonym;
        }
        ValueNode result = canonicalizeConditional(condition, trueValue, falseValue, combineStamps(condition, trueValue, falseValue, view), view, null);
        if (result != null) {
            return result;
        }
        return new ConditionalNode(condition, trueValue, falseValue);
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(combineStamps(condition, trueValue, falseValue, NodeView.DEFAULT));
    }

    private static Stamp combineStamps(LogicNode condition, ValueNode trueValue, ValueNode falseValue, NodeView view) {
        ValueNode asMinMax = MinMaxNode.fromConditional(condition, trueValue, falseValue, view);
        if (asMinMax != null) {
            return asMinMax.stamp(view);
                }
        return trueValue.stamp(view).meet(falseValue.stamp(view));
    }

    public ValueNode trueValue() {
        return trueValue;
    }

    public ValueNode falseValue() {
        return falseValue;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        NodeView view = NodeView.from(tool);
        ValueNode synonym = findSynonym(condition, trueValue(), falseValue(), view);
        if (synonym != null) {
            return synonym;
        }

        ValueNode result = canonicalizeConditional(condition, trueValue(), falseValue(), stamp, view, tool);
        if (result != null) {
            return result;
        }

        return this;
    }

    public static ValueNode canonicalizeConditional(LogicNode condition, ValueNode trueValue, ValueNode falseValue, Stamp stamp, NodeView view, CanonicalizerTool canonicalizer) {
        if (trueValue == falseValue) {
            // veriopt: ConditionalEqualBranches: (cond ? v : v) |-> v
            return trueValue;
        }

        if (condition instanceof CompareNode && ((CompareNode) condition).isIdentityComparison()) {
            // optimize the pattern (x == y) ? x : y
            CompareNode compare = (CompareNode) condition;
            if ((compare.getX() == trueValue && compare.getY() == falseValue) || (compare.getX() == falseValue && compare.getY() == trueValue)) {
                // veriopt: ConditionalEqualIsRHS: ((x == y) ? x : y) |-> y
                return falseValue;
            }
        }

        if (trueValue.stamp(view) instanceof IntegerStamp) {
            // check if the conditional is redundant
            if (condition instanceof IntegerLessThanNode) {
                IntegerLessThanNode lessThan = (IntegerLessThanNode) condition;
                IntegerStamp falseValueStamp = (IntegerStamp) falseValue.stamp(view);
                IntegerStamp trueValueStamp = (IntegerStamp) trueValue.stamp(view);
                if (lessThan.getX() == trueValue && lessThan.getY() == falseValue) {
                    // return "x" for "x < y ? x : y" in case that we know "x <= y"
                    if (trueValueStamp.upperBound() <= falseValueStamp.lowerBound()) {
                        // @formatter:off veriopt: ConditionalEliminateKnownLess: (x < y ? x : y) |-> x when (x.stamp.upper <= y.stamp.lower)
                        return trueValue;
                    }
                } else if (lessThan.getX() == falseValue && lessThan.getY() == trueValue) {
                    // return "y" for "x < y ? y : x" in case that we know "x <= y"
                    if (falseValueStamp.upperBound() <= trueValueStamp.lowerBound()) {
                        // @formatter:off veriopt: ConditionalEliminateKnownLess_2: (x < y ? y : x) |-> y when (x.stamp.upper <= y.stamp.lower)
                        return trueValue;
                    }
                }
            }

            // veriopt: TODO pick up here

            // this optimizes the case where a value from the range 0 - 1 is mapped to the
            // range 0 - 1
            if (trueValue.isConstant() && falseValue.isConstant()) {
                long constTrueValue = trueValue.asJavaConstant().asLong();
                long constFalseValue = falseValue.asJavaConstant().asLong();
                if (condition instanceof IntegerEqualsNode) {
                    IntegerEqualsNode equals = (IntegerEqualsNode) condition;
                    if (equals.getY().isConstant() && equals.getX().stamp(view) instanceof IntegerStamp) {
                        IntegerStamp equalsXStamp = (IntegerStamp) equals.getX().stamp(view);
                        if (equalsXStamp.mayBeSet() == 1) {
                            long equalsY = equals.getY().asJavaConstant().asLong();
                            if (equalsY == 0) {
                                if (constTrueValue == 0 && constFalseValue == 1) {
                                    // return x when: x == 0 ? 0 : 1;

                                    // veriopt: normalizeX: x == 0 ? 0 : 1 |-> x (when (stamp_expr x = IntegerStamp xBits lo hi) && x.upMask == 1) // todo sure about encoding
                                    return IntegerConvertNode.convertUnsigned(equals.getX(), stamp, view);
                                } else if (constTrueValue == 1 && constFalseValue == 0) {
                                    // negate a boolean value via xor

                                    // veriopt: flipX: x == 0 ? 1 : 0 |-> (x ^ 1) (when (stamp_expr x = IntegerStamp xBits lo hi) && x.upMask == 1) // todo sure about encoding
                                    return IntegerConvertNode.convertUnsigned(XorNode.create(equals.getX(), ConstantNode.forIntegerStamp(equals.getX().stamp(view), 1), view), stamp, view);
                                }
                            } else if (equalsY == 1) {
                                if (constTrueValue == 1 && constFalseValue == 0) {
                                    // return x when: x == 1 ? 1 : 0;

                                    // veriopt: normalizeX2: x == 1 ? 1 : 0 |-> x (when (stamp_expr x = IntegerStamp xBits lo hi) && x.upMask == 1) // todo sure about encoding
                                    return IntegerConvertNode.convertUnsigned(equals.getX(), stamp, view);
                                } else if (constTrueValue == 0 && constFalseValue == 1) {
                                    // negate a boolean value via xor

                                    // veriopt: flipX2: x == 1 ? 0 : 1 |-> (x ^ 1) (when (stamp_expr x = IntegerStamp xBits lo hi) && x.upMask == 1) // todo sure about encoding
                                    return IntegerConvertNode.convertUnsigned(XorNode.create(equals.getX(), ConstantNode.forIntegerStamp(equals.getX().stamp(view), 1), view), stamp, view);
                                }
                            }
                        }
                    }
                } else if (condition instanceof IntegerTestNode) {
                    // replace IntegerTestNode with AndNode for the following patterns:
                    // (value & 1) == 0 ? 0 : 1
                    // (value & 1) == 1 ? 1 : 0
                    IntegerTestNode integerTestNode = (IntegerTestNode) condition;
                    if (integerTestNode.getY().isConstant() && integerTestNode.getX().stamp(view) instanceof IntegerStamp) {
                        long testY = integerTestNode.getY().asJavaConstant().asLong();
                        if (testY == 1 && constTrueValue == 0 && constFalseValue == 1) {

                            // veriopt: OptimiseIntegerTest: (x & 1) == 0 ? 0 : 1 |-> (x & 1)

                            // todo if x is between 0-1, then this can be optimised to just return x.
                            //  Optimisation might not be worth the extra checks though
                            /*
                               if x = 0:                      |  if x = 1:
                                                              |
                               - (x & 1) == 0 ? 0 : 1         |  - (x & 1) == 0 ? 0 : 1
                               - (0 & 1) == 0 ? 0 : 1         |  - (1 & 1) == 0 ? 0 : 1
                               - true ? 0 : 1                 |  - false ? 0 : 1
                               - 0                            |  - 1
                            */
                            return IntegerConvertNode.convertUnsigned(AndNode.create(integerTestNode.getX(), integerTestNode.getY(), view), stamp, view);
                        }
                    }
                }
            }

            if (condition instanceof IntegerLessThanNode) {
                /*
                 * Convert a conditional add ((x < 0) ? (x + y) : x) into (x + (y & (x >> (bits -
                 * 1)))) to avoid the test.
                 */
                IntegerLessThanNode lt = (IntegerLessThanNode) condition;
                if (lt.getY().isDefaultConstant()) {
                    if (falseValue == lt.getX()) {
                        if (trueValue instanceof AddNode) {
                            AddNode add = (AddNode) trueValue;
                            if (add.getX() == falseValue) {
                                int bits = ((IntegerStamp) trueValue.stamp(NodeView.DEFAULT)).getBits();
                                ValueNode shift = new RightShiftNode(lt.getX(), ConstantNode.forIntegerBits(32, bits - 1));
                                ValueNode and = new AndNode(shift, add.getY());

                                // todo not sure about encoding
                                // veriopt: ConvertTernaryIntoShift: ((x < 0) ? (x + y) : x) |->
                                //                                    (x + (y & (x >> (bits - 1)))) when (bits = (x + y).getBits)
                                return new AddNode(add.getX(), and);
                            }
                        }
                    }
                }
            }
        }

        /*
         * Convert `x < 0.0 ? Math.ceil(x) : Math.floor(x)` to RoundNode(x, TRUNCATE).
         */
        if (canonicalizer != null &&
                canonicalizer.supportsRounding() &&
                condition instanceof FloatLessThanNode &&
                trueValue instanceof RoundNode &&
                        falseValue instanceof RoundNode) {
            FloatLessThanNode lessThan = (FloatLessThanNode) condition;
            RoundNode trueRound = (RoundNode) trueValue;
            RoundNode falseRound = (RoundNode) falseValue;

            if (trueRound.getValue() == falseRound.getValue()) {
                ValueNode roundInput = trueRound.getValue();

                // Account for the fact that x might be compared as a float but converted to double
                // for rounding: `x < 0.0f ? Math.ceil((double) x) : Math.floor((double) x)`.
                ValueNode originalRoundInput = roundInput;
                if (roundInput instanceof FloatConvertNode && ((FloatConvertNode) roundInput).op == FloatConvert.F2D) {
                    originalRoundInput = ((FloatConvertNode) roundInput).getValue();
                }

                boolean isTruncate = false;
                if (lessThan.getX() == originalRoundInput && lessThan.getY().isDefaultConstant() &&
                                trueRound.mode() == RoundingMode.UP && falseRound.mode() == RoundingMode.DOWN) {
                                // x < 0.0 ? ceil(x) : floor(x)
                    isTruncate = true;
                } else if (lessThan.getX().isDefaultConstant() && lessThan.getY() == originalRoundInput &&
                                trueRound.mode() == RoundingMode.DOWN && falseRound.mode() == RoundingMode.UP) {
                                // 0.0 < x ? floor(x) : ceil(x)
                    isTruncate = true;
                }

                if (isTruncate) {
                    return new RoundNode(roundInput, RoundingMode.TRUNCATE);
                }
            }
        }

            // veriopt: FloatTruncateTernary1: x < 0.0 ? ceil(x) : floor(x) |-> RoundNode(x, TRUNCATE)
            // veriopt: FloatTruncateTernary2: 0.0 < x ? floor(x) : ceil(x) |-> RoundNode(x, TRUNCATE)
            if (condition instanceof IsNullNode && trueValue.isJavaConstant() && trueValue.asJavaConstant().isDefaultForKind() &&
                        falseValue == ((IsNullNode) condition).getValue()) {
            return falseValue;
        }

        return null;
    }

    private static ValueNode findSynonym(ValueNode condition, ValueNode trueValue, ValueNode falseValue, NodeView view) {
        if (condition instanceof LogicNegationNode) {
            LogicNegationNode negated = (LogicNegationNode) condition;

            // veriopt: NegateConditionFlipBranches: (~c) ? c_1 : c_2 |-> c ? c_2 : c_1
            return ConditionalNode.create(negated.getValue(), falseValue, trueValue, view);
        }
        if (condition instanceof LogicConstantNode) {
            LogicConstantNode c = (LogicConstantNode) condition;
            if (c.getValue()) {

                // veriopt: DefaultTrueBranch: true ? c_1 : c_2 |-> c_1
                return trueValue;
            } else {
                return falseValue;

                // veriopt: DefaultFalseBranch: false ? c_1 : c_2 |-> c_2
            }
        }
        return null;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.emitConditional(this);
    }

    public ConditionalNode(StructuredGraph graph, CanonicalCondition condition, ValueNode x, ValueNode y) {
        this(createCompareNode(graph, condition, x, y, null, NodeView.DEFAULT));
    }
}
