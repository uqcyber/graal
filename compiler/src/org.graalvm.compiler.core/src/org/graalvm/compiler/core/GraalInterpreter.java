package org.graalvm.compiler.core;

import jdk.vm.ci.meta.JavaConstant;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BinaryOpLogicNode;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;


public class GraalInterpreter {
    private final Map<Node, RuntimeType> state = new HashMap<>();
    private final Map<Integer, RuntimeType> offsetMapping = new HashMap<>(); //data offsets to values stored todo deprecate
    private final Map<Node, Integer>  mergeIndexMapping = new HashMap<>(); // for Merge Nodes to 'remember' which Phi to eval
    private final ArrayList<String> errorMessages = new ArrayList<>();
    private boolean logging;

    public GraalInterpreter(boolean shouldLog){
        this.logging = shouldLog;
    }

    private void log_string(String input){
        if (logging) {
            System.err.println(input);
        }
    }

    public Map<Node, RuntimeType> getState(){
        return state;
    }

    public void setLogging(boolean shouldLog){
        logging = shouldLog;
    }

    public RuntimeType executeGraph(StructuredGraph graph){ //todo throw exception
        log_string("-------------------------------Now executing graph-----------------------------\n");

        ControlFlowVisit controlVisit = new ControlFlowVisit();
        graph.start().accept(controlVisit);
        // Note, in the visit methods, node.next().accept(this) is used
        // rather than (compile time typed) visit(node.next());
        for (Node node : graph.getNodes()){
            if (node instanceof ReturnNode){ // todo Currently assumes only one "return" is encountered
                log_string(String.format("The return value was: %s\n", state.get(node)));
                return state.get(node);
            }
        }

        if (!errorMessages.isEmpty()){
            System.err.println("Encountered the following errors: ");
            for (String err : errorMessages) {
                log_string(err);
            }
        }

        return null;

        //todo check if interpreter execution should return anything, e.g. list of steps taken, final runtime type?
    }

    //////////////////////////// Control Flow Visit Methods ///////////////////////////////////////////

    public class ControlFlowVisit implements NodalVisitor {
        public RuntimeType visit(ValueNode node) {
            // todo throw exception
            log_string("Encountered CONTROL base case: " + node + node.getClass() + " (node type not handled yet!)\n");
            //throw new Exception("Node type" + node + " not yet implemented error.");
            return null;
        }

        public RuntimeType visit(StartNode node){
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            node.next().accept(this);
            return null;
        }

        public RuntimeType visit(BeginNode node){
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            node.next().accept(this); // todo deal with frame states etc.
            return null;
        }

        public RuntimeType visit(EndNode node){
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            for (Node nextNode : node.cfgSuccessors()){ //Should just have one successor

                AbstractMergeNode mergeNode = (AbstractMergeNode) nextNode;
                int index = mergeNode.phiPredecessorIndex(node);
                log_string("Mapping " + mergeNode + " to index " + index + " (" +  node + ")");
                mergeIndexMapping.put(mergeNode, index);
                mergeNode.accept(this);
            }
            return null;
        }

        public RuntimeType visit(LoopEndNode node) {
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");

            LoopBeginNode loopBeginNode = node.loopBegin();
            int phiIndex = loopBeginNode.phiPredecessorIndex(node);
            log_string("The phi index for the loopBegin node following: " +  node + " is " + phiIndex);
            mergeIndexMapping.put(loopBeginNode, phiIndex);

            loopBeginNode.accept(this);
            return null;
        }

        private void applyMerge(AbstractMergeNode node){
            // Gets the index from which this merge node was reached.
            int accessIndex = mergeIndexMapping.get(node);
            // Get all associated phi nodes of this merge node
            // Evaluate all associated phi nodes with merge access index as their input
            log_string("Evaluating phis for " + node + " at index " + accessIndex);
            for (Node phi: node.phis()) {
                log_string("---- Start " + phi + " Evaluation ------");
                RuntimeType phiVal = ((PhiNode) phi).valueAt(accessIndex).accept(new DataFlowVisit());
                log_string("---- End " + phi + " Evaluation ------");
                state.put(phi, phiVal);
            }
        }

        public RuntimeType visit(MergeNode node) {
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            // Evaluates the phis associated with the given merge node.
            applyMerge(node);
            // Continue to following cfg node
            node.next().accept(this);
            return null;
        }

        public RuntimeType visit(LoopBeginNode node) {
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            // Evaluates the phis associated with the given merge node.
            applyMerge(node);
            // Continue to following cfg node
            node.next().accept(this);
            return null;
        }

        @Override //todo
        public RuntimeType visit(LoopExitNode node) {
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            node.next().accept(this);
            return null;
        }

        public RuntimeType visit(ValueProxyNode node) {
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(StoreFieldNode node){
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");

            log_string(String.format("Storing value: %s in %s - specifically in offset  %s\n", node.value(), node.field(), node.field().getOffset()));

            RuntimeType value = node.value().accept(new DataFlowVisit());
            offsetMapping.put(node.field().getOffset(), value);

            node.next().accept(this); //as opposed to: (compile time typed) visit(node.next());
            return null;
        }

        public RuntimeType visit(LoadFieldNode node){
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            // Execute LoadFieldNode eagerly as control flow edges are traversed
            // Assign value from offset to node. - may be null
            int offset = node.field().getOffset();
            RuntimeType valueAtOffset = offsetMapping.get(offset);

            state.put(node, valueAtOffset);

            node.next().accept(this);
            return null;
        }

        public RuntimeType visit(AddNode node) {
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        @Override
        public RuntimeType visit(SubNode node) {
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        @Override
        public RuntimeType visit(MulNode node) {
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        @Override
        public RuntimeType visit(RightShiftNode node) {
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        @Override
        public RuntimeType visit(LeftShiftNode node) {
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        @Override
        public RuntimeType visit(UnsignedRightShiftNode node) {
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(IfNode node) {
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");

            RuntimeType condition = node.condition().accept(new DataFlowVisit());

            if (condition.getBoolean()){
                log_string("The condition evaluated to true");
                node.trueSuccessor().accept(this);
            } else {
                log_string("The condition evaluated to False");
                node.falseSuccessor().accept(this);
            }

            return null;
        }

        public RuntimeType visit(IntegerLessThanNode node) {
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }


        public RuntimeType visit(ParameterNode node) {
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(ReturnNode node) {
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            if (node.result() != null){ // May have return node with no associated result ValueNode
                RuntimeType out = node.result().accept(new DataFlowVisit());
                state.put(node, out);
            } else {
                state.put(node, new RTVoid());
            }

            return null;
        }

        public RuntimeType visit(ConstantNode node) {
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(ValuePhiNode node) {
            log_string("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }
    }

    //////////////////////////// Data Flow Visit Methods ///////////////////////////////////////////

    public class DataFlowVisit implements NodalVisitor {
        public RuntimeType visit(ValueNode node) {
            log_string("Encountered DATA base case: " + node + node.getClass() + " (node type not handled yet!)\n");
            return null;
        }

        public RuntimeType visit(StartNode node) {
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(BeginNode node) {
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(EndNode node) {
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(StoreFieldNode node) {
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(LoadFieldNode node){
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            RuntimeType value = state.get(node);

            if (value != null){
                return value;
            } else {
                log_string(String.format("No data stored in field - returning GARBAGE from %s\n\n", node.field().getOffset()));
                errorMessages.add( String.format("Load from field without stored value (ID: %s, Field Offset: %s)", node.id(), node.field().getOffset()));
                return new RTInteger(1);
            }
        }

        //Helper function that takes a binary method for node node operations - factor out add/sub/...
        // Would need to define a separate method for BinaryOpLogic Node,
        private RuntimeType binary_operation_helper(BinaryNode node, BinaryOperator<RTInteger> operator) {
            RuntimeType x_value = node.getX().accept(this);
            RuntimeType y_value = node.getY().accept(this);

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
            RuntimeType x_value = node.getX().accept(this);
            RuntimeType y_value = node.getY().accept(this);

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
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
//            return general_binary_helper(node, RTInteger::sub);
            return binary_operation_helper(node, RTInteger::sub);
        }

        public RuntimeType visit(MulNode node) {
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return binary_operation_helper(node, RTInteger::mul);
        }

        @Override //todo test
        public RuntimeType visit(RightShiftNode node) {
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return binary_operation_helper(node, RTInteger::rightShift);
        }

        @Override
        public RuntimeType visit(LeftShiftNode node) {
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return binary_operation_helper(node, RTInteger::leftShift);
        }

        @Override
        public RuntimeType visit(UnsignedRightShiftNode node) {
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return binary_operation_helper(node, RTInteger::unsignedRightShift);
        }

        public RuntimeType visit(AddNode node) {
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return binary_operation_helper(node, RTInteger::add);
        }

        public RuntimeType visit(IntegerLessThanNode node) {
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return binary_operation_helper(node, RTInteger::lessThan);
        }

        public RuntimeType visit(IfNode node) {
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        @Override //todo
        public RuntimeType visit(ParameterNode node) {
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            log_string("TODO implement parameter data retrieval: Generating fake data");
            return new RTInteger(2);
        }

        public RuntimeType visit(LoopBeginNode node) {
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(LoopEndNode node) {
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(LoopExitNode node) {
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(ValueProxyNode node) {
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return node.getOriginalNode().accept(this);
        }

        public RuntimeType visit(ReturnNode node) {
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(ConstantNode node){
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return new RTInteger(((JavaConstant) node.getValue()).asInt());
        }

        public RuntimeType visit(MergeNode node) {
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(ValuePhiNode node) {
            log_string("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return state.get(node);
        }
    }
}