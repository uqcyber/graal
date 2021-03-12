package org.graalvm.compiler.nodes;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.*;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;



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
}
