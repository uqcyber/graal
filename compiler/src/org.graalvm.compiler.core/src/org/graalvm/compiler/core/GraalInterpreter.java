package org.graalvm.compiler.core;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BinaryOpLogicNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.NodalVisitor;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.RuntimeType;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.ValueProxyNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.UnsignedRightShiftNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;

// Custom Runtime Types
import org.graalvm.compiler.core.runtimetypes.RTInteger;
import org.graalvm.compiler.core.runtimetypes.RTVoid;

import org.graalvm.compiler.phases.tiers.HighTierContext;

// Reflection methods for dispatch without Visitor pattern
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;


public class GraalInterpreter {
    private final Map<Node, RuntimeType> state = new HashMap<>();
    private final Map<Integer, RuntimeType> offsetMapping = new HashMap<>(); //data offsets to values stored todo deprecate
    private final Map<Node, Integer>  mergeIndexMapping = new HashMap<>(); // for Merge Nodes to 'remember' which Phi to eval
    private final ArrayList<String> errorMessages = new ArrayList<>();
    private final GraalInterpreter interpreter = this;
    private int indent = -1;

    // Stack containing lists of params. Invoke nodes push new param lists,
    private final ArrayList<ArrayList<ValueNode>> paramStack = new ArrayList<>();

    private boolean logging;
    private final HighTierContext context;

    public GraalInterpreter(HighTierContext context, boolean shouldLog){
        this.paramStack.add(new ArrayList<>());
        this.logging = shouldLog;
        this.context = context;
    }

    private void log(String input){
        if (logging) {
            String spaces = new String(new char[indent*4]).replace("\0", " ");
            System.err.println(spaces + input);
        }
    }

    public Map<Node, RuntimeType> getState(){
        return state;
    }

    public void setLogging(boolean shouldLog){
        logging = shouldLog;
    }

    public RuntimeType executeGraph(StructuredGraph graph){ //todo throw exception
        indent = indent + 1;
        log("-------------------------------Now executing graph-----------------------------\n");

        ControlFlowVisit controlVisit = new ControlFlowVisit();
        execute(controlVisit, graph.start());

        if (!errorMessages.isEmpty()){
            System.err.println("Encountered the following errors: ");
            for (String err : errorMessages) {
                log(err);
            }
        }

        // Note, in the visit methods, execute(NodalVisitor, node) is used rather than:
        // Previously, more common visitor implementation:
        // node.next().accept(this); -- deprecated to avoid having to modify all nodes.
        // or compile time typed visit(node.next())
        for (Node node : graph.getNodes()){
            if (node instanceof ReturnNode){ // todo Currently assumes only one "return" is encountered
                log(String.format("The return value was: %s\n", state.get(node)));
                log("-------------------------------End graph execution---------------------------\n");
                indent = indent - 1;
                return state.get(node);
            }
        }

        return null;
        //todo check if interpreter execution should return anything, e.g. list of steps taken, final runtime type?
    }

    private RuntimeType execute(NodalVisitor visitor, Node node) {
        // todo use nodeClass info?
        // todo cache classType to Method results in mapping
        // todo ensure that the correct method is chosen / mapped
        // Using generic 'unbounded wildcard' as the type fo the node can be any subclass of Object
        Class<?>[] args = new Class<?>[1];
        args[0] = node.getClass();
        Method matchingMethod;

        // Determine visitor type, then get corresponding method
        try {
            if (visitor instanceof ControlFlowVisit){
                matchingMethod = ControlFlowVisit.class.getMethod("visit", args);
            } else {
                matchingMethod = DataFlowVisit.class.getMethod("visit", args);
            }
        } catch (NoSuchMethodException e) {
            log("Method not yet defined %s");
            e.printStackTrace();
            return null;
        }
        // log(String.format("Method found was %s", matchingMethod));
        try{
            // System.out.println(visitor);
            //todo check cast to RuntimeType
            return (RuntimeType) matchingMethod.invoke(visitor, node);
        } catch (Exception e){
            log(String.format("Exception encountered with invoke attempt: %s", e));
            e.printStackTrace();
        }

        return null;
    }
    //////////////////////////// Control Flow Visit Methods ///////////////////////////////////////////

    public class ControlFlowVisit implements NodalVisitor {
        public RuntimeType visit(ValueNode node) {
            // todo throw exception
            log("Encountered CONTROL base case: " + node + node.getClass() + " (node type not handled yet!)\n");
            //throw new Exception("Node type" + node + " not yet implemented error.");
            return null;
        }

        public RuntimeType visit(StartNode node){
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            execute(this, node.next());
            return null;
        }

        public RuntimeType visit(BeginNode node){
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            execute(this, node.next()); // todo deal with frame states etc.
            return null;
        }

        public RuntimeType visit(EndNode node){
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            for (Node nextNode : node.cfgSuccessors()){ //Should just have one successor

                AbstractMergeNode mergeNode = (AbstractMergeNode) nextNode;
                int index = mergeNode.phiPredecessorIndex(node);
                log("Mapping " + mergeNode + " to index " + index + " (" +  node + ")");
                mergeIndexMapping.put(mergeNode, index);
                execute(this, mergeNode);
            }
            return null;
        }

        public RuntimeType visit(LoopEndNode node) {
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");

            LoopBeginNode loopBeginNode = node.loopBegin();
            int phiIndex = loopBeginNode.phiPredecessorIndex(node);
            log("The phi index for the loopBegin node following: " +  node + " is " + phiIndex);
            mergeIndexMapping.put(loopBeginNode, phiIndex);

            execute(this, loopBeginNode);
            return null;
        }

        private void applyMerge(AbstractMergeNode node){
            // Gets the index from which this merge node was reached.
            int accessIndex = mergeIndexMapping.get(node);
            // Get all associated phi nodes of this merge node
            // Evaluate all associated phi nodes with merge access index as their input
            log("Evaluating phis for " + node + " at index " + accessIndex);
            for (PhiNode phi: node.phis()) {
                log("---- Start " + phi + " Evaluation ------");
                RuntimeType phiVal = execute(new DataFlowVisit(), phi.valueAt(accessIndex));
                log("---- End " + phi + " Evaluation ------");
                state.put(phi, phiVal);
            }
        }

        public RuntimeType visit(MergeNode node) {
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            // Evaluates the phis associated with the given merge node.
            applyMerge(node);
            // Continue to following cfg node
            execute(this, node.next());
            return null;
        }

        public RuntimeType visit(LoopBeginNode node) {
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            // Evaluates the phis associated with the given merge node.
            applyMerge(node);
            // Continue to following cfg node
            execute(this, node.next());
            return null;
        }

        public RuntimeType visit(LoopExitNode node) {
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            execute(this, node.next());
            return null;
        }

        public RuntimeType visit(ValueProxyNode node) {
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        private StructuredGraph create_subgraph(CallTargetNode callNode){
            // Construct a graph for method associated with call node. todo store prev. constructed methods?
            ResolvedJavaMethod nodeMethod = callNode.targetMethod();
            StructuredGraph.Builder builder = new StructuredGraph.Builder(
                    callNode.getOptions(),
                    callNode.getDebug(),
                    StructuredGraph.AllowAssumptions.YES).method(nodeMethod);
            StructuredGraph methodGraph = builder.build();
            context.getGraphBuilderSuite().apply(methodGraph, context);

            return methodGraph;
        }

        public RuntimeType visit(InvokeNode node) {
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");

            CallTargetNode callNode = node.callTarget();

            // todo could rework to use native NodeInputList
            ArrayList<ValueNode> arguments = new ArrayList<>(Arrays.asList(callNode.arguments().toArray(new ValueNode[0])));
            log(String.format("The arguments supplied to the invoke are %s", arguments));
            paramStack.add(0, arguments); // adds parameters for method call to top of stack.

            StructuredGraph methodGraph = create_subgraph(callNode);
            RuntimeType methodOut = interpreter.executeGraph(methodGraph);

            state.put(node, methodOut);  // Used when visiting Invoke node as Data
            log(String.format("The returned value from the function call was: %s", methodOut.toString()) );

            execute(this, node.next());
            return null;
        }

        public RuntimeType visit(StoreFieldNode node){
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            log(String.format("Storing value: %s in %s - specifically in offset  %s\n", node.value(), node.field(), node.field().getOffset()));

            RuntimeType value = execute(new DataFlowVisit(), node.value());
            offsetMapping.put(node.field().getOffset(), value);

            execute(this, node.next());
            return null;
        }

        public RuntimeType visit(LoadFieldNode node){
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            // Execute LoadFieldNode eagerly as control flow edges are traversed
            // Assign value from offset to node. - may be null
            int offset = node.field().getOffset();
            RuntimeType valueAtOffset = offsetMapping.get(offset);

            state.put(node, valueAtOffset);

            execute(this, node.next());
            return null;
        }

        public RuntimeType visit(AddNode node) {
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(SubNode node) {
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(MulNode node) {
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(RightShiftNode node) {
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(LeftShiftNode node) {
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(UnsignedRightShiftNode node) {
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(IfNode node) {
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");

            RuntimeType condition = execute(new DataFlowVisit(), node.condition());

            assert condition != null;
            if (condition.getBoolean()){
                log("The condition evaluated to true");
                execute(this, node.trueSuccessor());
            } else {
                log("The condition evaluated to False");
                execute(this, node.falseSuccessor());
            }

            return null;
        }

        public RuntimeType visit(IntegerLessThanNode node) {
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }


        public RuntimeType visit(ParameterNode node) {
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(ReturnNode node) {
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            if (node.result() != null){ // May have return node with no associated result ValueNode
                RuntimeType out = execute(new DataFlowVisit(), node.result());
                state.put(node, out);
            } else {
                state.put(node, new RTVoid());
            }

            paramStack.remove(0); // for handling parameter variables in the stack.

            return null;
        }

        public RuntimeType visit(ConstantNode node) {
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(ValuePhiNode node) {
            log("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }
    }

    //////////////////////////// Data Flow Visit Methods ///////////////////////////////////////////

    public class DataFlowVisit implements NodalVisitor {
        public RuntimeType visit(ValueNode node) {
            log("Encountered DATA base case: " + node + node.getClass() + " (node type not handled yet!)\n");
            return null;
        }

        public RuntimeType visit(StartNode node) {
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(BeginNode node) {
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(EndNode node) {
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(StoreFieldNode node) {
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(LoadFieldNode node){
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            RuntimeType value = state.get(node);

            if (value != null){
                return value;
            } else {
                log(String.format("No data stored in field - returning GARBAGE from %s\n\n", node.field().getOffset()));
                errorMessages.add( String.format("Load from field without stored value (ID: %s, Field Offset: %s)", node.id(), node.field().getOffset()));
                return new RTInteger(1);
            }
        }

        //Helper function that takes a binary method for node node operations - factor out add/sub/...
        // Would need to define a separate method for BinaryOpLogic Node,
        private RuntimeType binary_operation_helper(BinaryNode node, BinaryOperator<RTInteger> operator) {
            RuntimeType x_value = execute(this, node.getX());
            RuntimeType y_value = execute(this, node.getY());

            if (!(x_value instanceof RTInteger) || !(y_value instanceof  RTInteger)){
                errorMessages.add( String.format("Invalid inputs to (%s): %s %s", node, x_value, y_value));
            }
            assert x_value instanceof RTInteger;
            assert y_value instanceof RTInteger;
            RTInteger x_integer = (RTInteger) x_value;
            RTInteger y_integer = (RTInteger) y_value;

            return operator.apply(x_integer, y_integer);
        }

        private RuntimeType binary_operation_helper(BinaryOpLogicNode node, BiFunction<RTInteger, RTInteger, RuntimeType> operator) {
            RuntimeType x_value = execute(this, node.getX());
            RuntimeType y_value = execute(this, node.getY());

            if (!(x_value instanceof RTInteger) || !(y_value instanceof  RTInteger)){
                errorMessages.add( String.format("Invalid inputs to (%s): %s %s", node, x_value, y_value));
            }
            assert x_value instanceof RTInteger;
            assert y_value instanceof RTInteger;
            RTInteger x_integer = (RTInteger) x_value;
            RTInteger y_integer = (RTInteger) y_value;

            return operator.apply(x_integer, y_integer);
        }

        //Uses reflection to access getter methods.
        /*
        private RuntimeType general_binary_helper(FloatingNode node, BiFunction<RTInteger, RTInteger, RuntimeType> operator) {
            RuntimeType x_value;
            RuntimeType y_value;
            try {
                Method getX = node.getClass().getMethod("getX");
                Method getY = node.getClass().getMethod("getY");
                x_value = ((ValueNode) getX.invoke(node)).accept(this);
                y_value = ((ValueNode) getY.invoke(node)).accept(this);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
                return null;
            }
            if (!(x_value instanceof RTInteger) || !(y_value instanceof  RTInteger)){
                errorMessages.add( String.format("Invalid inputs to (%s): %s %s", node, x_value, y_value));
            }
            assert x_value instanceof RTInteger;
            assert y_value instanceof RTInteger;
            RTInteger x_integer = (RTInteger) x_value;
            RTInteger y_integer = (RTInteger) y_value;
            return operator.apply(x_integer, y_integer);
        }
         */

        public RuntimeType visit(SubNode node){
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            // return general_binary_helper(node, RTInteger::sub);
            return binary_operation_helper(node, RTInteger::sub);
        }

        public RuntimeType visit(MulNode node) {
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return binary_operation_helper(node, RTInteger::mul);
        }

        public RuntimeType visit(RightShiftNode node) {
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return binary_operation_helper(node, RTInteger::rightShift);
        }

        public RuntimeType visit(LeftShiftNode node) {
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return binary_operation_helper(node, RTInteger::leftShift);
        }

        public RuntimeType visit(UnsignedRightShiftNode node) {
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return binary_operation_helper(node, RTInteger::unsignedRightShift);
        }

        public RuntimeType visit(AddNode node) {
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return binary_operation_helper(node, RTInteger::add);
        }

        public RuntimeType visit(IntegerLessThanNode node) {
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return binary_operation_helper(node, RTInteger::lessThan);
        }

        public RuntimeType visit(IfNode node) {
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(ParameterNode node) {
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");

            ArrayList<ValueNode> parameters = paramStack.get(0);
            ValueNode paramValue;
            try {
                paramValue = parameters.get(node.index());
            } catch (IndexOutOfBoundsException e){
                errorMessages.add( String.format("Invalid parameter index %s in %s", node.index(), node) );
                log("Generating fake data");
                return new RTInteger(2);
            }
            return execute(this, paramValue);
        }

        public RuntimeType visit(LoopBeginNode node) {
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(LoopEndNode node) {
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(LoopExitNode node) {
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(ValueProxyNode node) {
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return execute(this, node.getOriginalNode());
        }

        public RuntimeType visit(InvokeNode node) {
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");

            return state.get(node); // todo assumes the invoke node has previously been visited in control flow.
        }

        public RuntimeType visit(ReturnNode node) {
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(ConstantNode node){
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return new RTInteger(((JavaConstant) node.getValue()).asInt());
        }

        public RuntimeType visit(MergeNode node) {
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(ValuePhiNode node) {
            log("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return state.get(node);
        }
    }
}