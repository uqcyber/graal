package org.graalvm.compiler.nodes;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.*;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;

// Remember to add the following to node files:
// public RuntimeType accept(NodalVisitor v){ return v.visit(this); }


public interface NodalVisitor { // Only need visit methods for leaf nodes
    RuntimeType visit(ValueNode node); // Considered base / most general case (as opposed to node due to module dependencies)
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
    RuntimeType visit(IfNode node);
    RuntimeType visit(IntegerLessThanNode node);
    RuntimeType visit(ParameterNode node);
    RuntimeType visit(LoopBeginNode node);
    RuntimeType visit(LoopEndNode node);
    RuntimeType visit(LoopExitNode node);
    RuntimeType visit(ValueProxyNode node);
}
