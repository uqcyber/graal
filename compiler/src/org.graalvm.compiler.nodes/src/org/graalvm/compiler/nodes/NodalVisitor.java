package org.graalvm.compiler.nodes;

import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.FloatEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.SignedDivNode;
import org.graalvm.compiler.nodes.calc.SignedRemNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.UnsignedRightShiftNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.debug.BlackholeNode;
import org.graalvm.compiler.nodes.debug.ControlFlowAnchorNode;
import org.graalvm.compiler.nodes.extended.AbstractBoxingNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.GetClassNode;
import org.graalvm.compiler.nodes.extended.OpaqueNode;
import org.graalvm.compiler.nodes.extended.StateSplitProxyNode;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.FinalFieldBarrierNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.RegisterFinalizerNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;

public interface NodalVisitor { // Only need visit methods for leaf nodes
    RuntimeType visit(StartNode node);

    RuntimeType visit(BeginNode node);

    RuntimeType visit(EndNode node);

    RuntimeType visit(StoreFieldNode node);

    RuntimeType visit(ReturnNode node);

    RuntimeType visit(ConstantNode node);

    RuntimeType visit(MergeNode node);

    RuntimeType visit(ValuePhiNode node);

    RuntimeType visit(LoadFieldNode node);

    RuntimeType visit(AddNode node);

    RuntimeType visit(SubNode node);

    RuntimeType visit(MulNode node);

    RuntimeType visit(RightShiftNode node);

    RuntimeType visit(LeftShiftNode node);

    RuntimeType visit(UnsignedRightShiftNode node);

    RuntimeType visit(IfNode node);

    RuntimeType visit(IntegerLessThanNode node);

    RuntimeType visit(ParameterNode node);

    RuntimeType visit(LoopBeginNode node);

    RuntimeType visit(LoopEndNode node);

    RuntimeType visit(LoopExitNode node);

    RuntimeType visit(ValueProxyNode node);

    RuntimeType visit(InvokeNode node);

    RuntimeType visit(IntegerEqualsNode node);

    RuntimeType visit(FixedGuardNode node);

    RuntimeType visit(NewArrayNode node);

    RuntimeType visit(ArrayLengthNode node);

    RuntimeType visit(StoreIndexedNode node);

    RuntimeType visit(LoadIndexedNode node);

    RuntimeType visit(SignedDivNode node);

    RuntimeType visit(SignedRemNode node);

    RuntimeType visit(NewInstanceNode node);

    RuntimeType visit(RegisterFinalizerNode node);

    RuntimeType visit(FinalFieldBarrierNode node);

    RuntimeType visit(PiNode node);

    RuntimeType visit(UnboxNode node);

    RuntimeType visit(AbstractBoxingNode node); // todo separate handling for private subclass
                                                // AllocatingBoxNode?

    RuntimeType visit(ZeroExtendNode node); // todo check implementation for widening/narrowing
                                            // nodes

    RuntimeType visit(NarrowNode node);

    RuntimeType visit(ObjectEqualsNode node);

    RuntimeType visit(GetClassNode node);

    RuntimeType visit(BlackholeNode node); // stub debug

    RuntimeType visit(ControlFlowAnchorNode node); // stub debug

    RuntimeType visit(BranchProbabilityNode node);

    RuntimeType visit(DeoptimizeNode node);

    RuntimeType visit(ConditionalNode node);

    RuntimeType visit(InvokeWithExceptionNode node);

    RuntimeType visit(OpaqueNode node);

    RuntimeType visit(IsNullNode node);

    RuntimeType visit(KillingBeginNode node);

    RuntimeType visit(SignExtendNode node);

    RuntimeType visit(FloatEqualsNode node);

    RuntimeType visit(StateSplitProxyNode node);
    // RuntimeType visit(AMD64ArrayIndexOfDispatchNode node);
    // RuntimeType visit(UnsignedMulHighNode node);
    // will add a dependency on replacements (which creates a circular dependency between Java and Loop)
    // RuntimeType visit(ArrayCopyNode node);
}
