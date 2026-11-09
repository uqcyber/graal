/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.code.CodeUtil;

@NodeInfo(shortName = "/")
public class SignedDivNode extends IntegerDivRemNode implements LIRLowerable {

    public static final NodeClass<SignedDivNode> TYPE = NodeClass.create(SignedDivNode.class);

    public SignedDivNode(ValueNode x, ValueNode y, GuardingNode zeroCheck) {
        this(TYPE, x, y, zeroCheck);
    }

    protected SignedDivNode(NodeClass<? extends SignedDivNode> c, ValueNode x, ValueNode y, GuardingNode zeroCheck) {
        super(c, IntegerStamp.OPS.getDiv().foldStamp(x.stamp(NodeView.DEFAULT), y.stamp(NodeView.DEFAULT)), Op.DIV, Type.SIGNED, x, y, zeroCheck);
    }

    public static ValueNode create(ValueNode x, ValueNode y, GuardingNode zeroCheck, NodeView view) {
        return canonical(null, x, y, zeroCheck, view);
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(IntegerStamp.OPS.getDiv().foldStamp(getX().stamp(NodeView.DEFAULT), getY().stamp(NodeView.DEFAULT)));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        return canonical(this, forX, forY, getZeroGuard(), view, this.canDeoptimize() ? tool.divisionOverflowIsJVMSCompliant() : true);
    }

    /**
     * This is used as a hook to allow "safe" SignedDivNodes to be created during canonicalization.
     */
    protected SignedDivNode createWithInputs(ValueNode forX, ValueNode forY, GuardingNode forZeroCheck, FrameState forStateBefore) {
        SignedDivNode sd = new SignedDivNode(forX, forY, forZeroCheck);
        sd.stateBefore = forStateBefore;
        return sd;
    }

    public static ValueNode canonical(SignedDivNode self, ValueNode forX, ValueNode forY, GuardingNode zeroCheck, NodeView view) {
        return canonical(self, forX, forY, zeroCheck, view, self == null ? false : !self.canDeoptimize());
    }

    public static ValueNode canonical(SignedDivNode self, ValueNode forX, ValueNode forY, GuardingNode zeroCheck, NodeView view, boolean divisionOverflowIsJVMSCompliant) {
        Stamp predictedStamp = IntegerStamp.OPS.getDiv().foldStamp(forX.stamp(NodeView.DEFAULT), forY.stamp(NodeView.DEFAULT));
        Stamp stamp = self != null ? self.stamp(view) : predictedStamp;
        if (forX.isConstant() && forY.isConstant()) {
            long y = forY.asJavaConstant().asLong();
            if (y == 0) {
                /* This will trap, cannot canonicalize. */
                return self != null ? self : new SignedDivNode(forX, forY, zeroCheck);
            }
            // veriopt: SignedDivFoldConst: (const x / const y) |-> const (x / y) when (y = new_int b yv && yv != 0)
            return ConstantNode.forIntegerStamp(stamp, forX.asJavaConstant().asLong() / y);
        } else if (forY.isConstant()) {
            long c = forY.asJavaConstant().asLong();
            ValueNode v = canonical(forX, c, view);
            if (v != null) {
                return v;
            }
        }

        // Convert the expression ((a - a % b) / b) into (a / b).
        if (forX instanceof SubNode) {
            SubNode integerSubNode = (SubNode) forX;
            if (integerSubNode.getY() instanceof SignedRemNode) {
                SignedRemNode integerRemNode = (SignedRemNode) integerSubNode.getY();
                if (integerSubNode.stamp(view).isCompatible(stamp) && integerRemNode.stamp(view).isCompatible(stamp) && integerSubNode.getX() == integerRemNode.getX() &&
                                forY == integerRemNode.getY()) {
                    if (self != null) {
                        return self.createWithInputs(integerSubNode.getX(), forY, zeroCheck, self.stateBefore);
                    }
                    // veriopt-alias: expr_stamp = stamp_expr ((x - (x % y)) / y)

                    // veriopt: EliminateRedundantMod: ((x - (x % y)) / y) |-> x / y
                    //          when (compatible expr_stamp (stamp_expr (x - (x % y))) &&
                    //                compatible expr_stamp (stamp_expr (x % y)))
                    return new SignedDivNode(integerSubNode.getX(), forY, zeroCheck);
                }
            }
        }

        if (self != null && self.canFloat() && GraalOptions.FloatingDivNodes.getValue(self.getOptions()) && self.graph().isBeforeStage(GraphState.StageFlag.VALUE_PROXY_REMOVAL)) {
            IntegerStamp yStamp = (IntegerStamp) forY.stamp(view);
            if (!yStamp.contains(0) && divisionIsJVMSCompliant(forX, forY, divisionOverflowIsJVMSCompliant)) {
                // veriopt: TODO encoding
                return SignedFloatingIntegerDivNode.create(forX, forY, view, zeroCheck, divisionOverflowIsJVMSCompliant);
            }
        }

        if (self != null && self.next() instanceof SignedDivNode) {
            NodeClass<?> nodeClass = self.getNodeClass();
            if (self.next().getClass() == self.getClass() && nodeClass.equalInputs(self, self.next()) && self.valueEquals(self.next())) {
                // veriopt: todo not sure
                return self.next();
            }
        }

        return self != null ? self : new SignedDivNode(forX, forY, zeroCheck);
    }

    @Override
    public boolean canFloat() {
        return true;
    }

    /**
     * Determines if {@code dividend / divisor} complies with the JVM specification for {@code idiv}
     * and {@code ldiv}. From {@jvms 6.5} for {@code idiv}:
     *
     * <pre>
     * There is one special case that does not satisfy this rule: if the dividend is the
     * negative integer of largest possible magnitude for the int type, and the divisor
     * is -1, then overflow occurs, and the result is equal to the dividend. Despite the
     * overflow, no exception is thrown in this case.
     * </pre>
     *
     * @param platformIsCompliant true iff the target platform complies with the JVM specification
     *            for the overflow case of {@code Integer.MIN_VALUE / -1} and
     *            {@code Long.MIN_VALUE / -1}
     *
     * @return true iff {@code platformIsCompliant == true} or the stamps of {@code dividend}
     *         {@code divisor} excludes the possibility of the problematic overflow case
     */
    public static boolean divisionIsJVMSCompliant(ValueNode dividend, ValueNode divisor, boolean platformIsCompliant) {
        if (platformIsCompliant) {
            return true;
        }
        IntegerStamp dividendStamp = (IntegerStamp) dividend.stamp(NodeView.DEFAULT);
        IntegerStamp divisorStamp = (IntegerStamp) divisor.stamp(NodeView.DEFAULT);
        assert dividendStamp.getBits() == divisorStamp.getBits() : Assertions.errorMessage(dividend, divisor);
        long minValue = NumUtil.minValue(dividendStamp.getBits());
        return !(dividendStamp.contains(minValue) && divisorStamp.contains(-1));
    }

    public static ValueNode canonical(ValueNode forX, long c, NodeView view) {
        if (c == 1) {
            // veriopt: DivisionByOneIsSelf: a / const(1) |-> a
            return forX;
        }
        if (c == -1) {
            // veriopt: DivisionByNegativeOneIsNegativeSelf: a / const(-1) |-> -a
            return NegateNode.create(forX, view);
        }
        if (NumUtil.absOverflows(c, IntegerStamp.getBits(forX.stamp(view)))) {
            return null;
        }
        long abs = NumUtil.safeAbs(c, forX);
        if (CodeUtil.isPowerOf2(abs) && forX.stamp(view) instanceof IntegerStamp) {
            IntegerStamp stampX = (IntegerStamp) forX.stamp(view);
            ValueNode dividend = forX;
            int log2 = CodeUtil.log2(abs);
            // no rounding if dividend is positive or if its low bits are always 0
            if (stampX.canBeNegative() && (stampX.mayBeSet() & (abs - 1)) != 0) {
                int bits = PrimitiveStamp.getBits(forX.stamp(view));
                RightShiftNode sign = new RightShiftNode(forX, ConstantNode.forInt(bits - 1));
                UnsignedRightShiftNode round = new UnsignedRightShiftNode(sign, ConstantNode.forInt(bits - log2));
                dividend = BinaryArithmeticNode.add(dividend, round, view);
            }
            RightShiftNode shift = new RightShiftNode(dividend, ConstantNode.forInt(log2));

            // veriopt-defn: lowBitsAlwaysZero = (up(x) & ((abs yv) - 1)) == 0
            // veriopt-defn: stamp_pow_2_cond = (stamp_expr x = IntegerStamp xb lo hi && is_power_of_2 (abs yv))
            // veriopt-defn: bit_minus v = new_int xb (xb - v)

            // veriopt-alias: shift_rhs = new_int xb (log2 (abs yv))
            // veriopt-alias: true_shift = ((x + ((x >> (bit_minus 1)) >>> (bit_minus (log2 (abs yv))))) >> shift_rhs)
            // veriopt-alias: false_shift = (x >> shift_rhs)

            if (c < 0) {
                // TODO two cases: either above rounding if-statement ran, or it did not.
                // todo if ran
                // veriopt: TrueCLessZero: x / const (new_int yb yv) |-> -(true_shift)
                //          when (stamp_pow_2_cond && yv <s 0 &&                         // simple conditions
                //                can_be_negative (stamp_expr x) && ~lowBitsAlwaysZero)  // rounding conditions

                // todo if didn't
                // veriopt: FalseCLessZero: x / const (new_int yb yv) |-> -(false_shift)
                //          when (stamp_pow_2_cond && yv <s 0 &&                     // simple conditions
                //               (is_positive (stamp_expr x) || lowBitsAlwaysZero))  // rounding conditions
                return NegateNode.create(shift, view);
            }
            // TODO two cases: either above rounding if-statement ran, or it did not.
            // todo if ran
            // veriopt: TrueCGeqZero: x / const (new_int yb yv) |-> true_shift
            //          when (stamp_pow_2_cond && yv >= 0 &&                         // simple conditions
            //                can_be_negative (stamp_expr x) && ~lowBitsAlwaysZero)  // rounding conditions

            // todo if didn't
            // veriopt: FalseCGeqZero: x / const (new_int yb yv) |-> false_shift
            //          when (stamp_pow_2_cond && yv >= 0 &&                     // simple conditions
            //               (is_positive (stamp_expr x) || lowBitsAlwaysZero))  // rounding conditions
            return shift;
        }
        return null;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().getArithmetic().emitDiv(gen.operand(getX()), gen.operand(getY()), gen.state(this)));
    }
}
