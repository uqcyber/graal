package org.graalvm.compiler.nodes.util;

import jdk.vm.ci.meta.ResolvedJavaField;
import org.graalvm.compiler.debug.interpreter.value.RuntimeValue;
import org.graalvm.compiler.debug.interpreter.value.RuntimeValueFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode;

import java.util.List;

public interface DebugInterpreterInterface {
    // TODO: proper JavaDocs

    // Used for nodes such NewArrayNode and NewInstanceNode
    void setHeapValue(Node node, RuntimeValue value);
    RuntimeValue getHeapValue(Node node);

    // Used to associate a control flow node with a value (when its looked up in a dataflow context)
    // after interpreting.
    void setNodeLookupValue(Node node, RuntimeValue value);
    RuntimeValue getNodeLookupValue(Node node);

    void setMergeNodeIncomingIndex(AbstractMergeNode node, int index);
    void visitMerge(AbstractMergeNode node);

    // Used for LoadFieldNode and StoreFieldNodes
    RuntimeValue loadFieldValue(ResolvedJavaField field);
    void storeFieldValue(ResolvedJavaField field, RuntimeValue value);

    // Used for ParameterNode
    List<RuntimeValue> getParameters();

    // Called by any node that needs to get the dataflow value of another node to use in its own interpretation
    RuntimeValue interpretDataflowNode(Node node);

    // used by InvokeNode to evaluate a call target.
    RuntimeValue interpretMethod(CallTargetNode target, List<Node> argumentNodes);

    // TODO: find a better way to do this
    RuntimeValueFactory getRuntimeValueFactory();
}
