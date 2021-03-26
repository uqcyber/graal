package org.graalvm.compiler.core;

import jdk.vm.ci.meta.JavaConstant;
import org.graalvm.compiler.nodes.AbstractMergeNode;
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
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class GraalInterpreter {
    private final Map<Node, RuntimeType> state = new HashMap<>();
    private final Map<Integer, RuntimeType> offsetMapping = new HashMap<>(); //data offsets to values stored todo deprecate
    private final Map<Node, Integer>  mergeIndexMapping = new HashMap<>(); // for Merge Nodes to 'remember' which Phi to eval
    private final ArrayList<String> errorMessages = new ArrayList<>();

    public Map<Node, RuntimeType> getState(){
        return state;
    }

    public void executeGraph(StructuredGraph graph){ //todo throw exception
        System.out.println("-------------------------------Now executing graph-----------------------------\n");

        ControlFlowVisit controlVisit = new ControlFlowVisit();
        graph.start().accept(controlVisit);
        // Note, in the visit methods, node.next().accept(this) is used
        // rather than (compile time typed) visit(node.next());
        for (Node node : graph.getNodes()){
            if (node instanceof ReturnNode){ // todo Currently assumes only one "return" is encountered
                System.out.printf("The return value was: %s\n", state.get(node));
            }
        }

        if (!errorMessages.isEmpty()){
            System.err.println("Encountered the following errors: ");
            for (String err : errorMessages) {
                System.out.println(err);
            }
        }
    }

    // todo Implement Number Type System (class RTNumber extends RuntimeType)

    static class RTBoolean extends RuntimeType {
        Boolean value;

        public RTBoolean(Boolean value) {
            super();
            this.value = value;
        }

        @Override
        public RuntimeType add(RuntimeType y_value) {
            return null;
        }

        @Override
        public Boolean getBoolean() {
            return value;
        }

        @Override
        public RuntimeType lessThan(RuntimeType b) {
            System.out.println("checking if boolean is less than some runtimeType " + b + "\n");
            return null;
        }
        @Override
        public String toString() {
            return super.toString() + " with Value (" + value + ")";
        }
    }

    // Represents a runtime Integer Object
    static class RTInteger extends RuntimeType {

        protected int value;

        public RTInteger(int number) {
            super();
            value = number;
        }

        public RTInteger add(RTInteger other){
            return new RTInteger(value + other.getValue());
        }

        public RTBoolean lessThan(RTInteger other){
            return new RTBoolean(this.value < other.getValue());
        }

        @Override
        public RTInteger add(RuntimeType y_value) {
            if (y_value instanceof  RTInteger){
                return this.add((RTInteger) y_value);
            }
            return null;
        }

        @Override
        public Boolean getBoolean() {
            return value > 0;
        }

        @Override
        public RTBoolean lessThan(RuntimeType b) {
            if (b instanceof RTInteger){
                return this.lessThan((RTInteger) b);
            }
            return new RTBoolean(false);
        }

        public int getValue() {
            return value;
        }

        @Override
        public String toString() {
            return super.toString() + " with Value (" + value + ")";
        }
    }

    //////////////////////////// Control Flow Visit Methods ///////////////////////////////////////////

    public class ControlFlowVisit implements NodalVisitor {
        public RuntimeType visit(ValueNode node) {
            // todo throw exception
            System.out.println("Encountered CONTROL base case: " + node + node.getClass() + " (node type not handled yet!)\n");
            //throw new Exception("Node type" + node + " not yet implemented error.");
            return null;
        }

        public RuntimeType visit(StartNode node){
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            node.next().accept(this);
            return null;
        }

        public RuntimeType visit(BeginNode node){
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            node.next().accept(this); // todo deal with frame states etc.
            return null;
        }

        public RuntimeType visit(EndNode node){
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            for (Node nextNode : node.cfgSuccessors()){ //Should just have one successor

                AbstractMergeNode mergeNode = (AbstractMergeNode) nextNode;
                int index = mergeNode.phiPredecessorIndex(node);
                System.out.println("Mapping " + mergeNode + " to index " + index + " (" +  node + ")");
                mergeIndexMapping.put(mergeNode, index);
                mergeNode.accept(this);
            }
            return null;
        }

        public RuntimeType visit(LoopEndNode node) {
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");

            LoopBeginNode loopBeginNode = node.loopBegin();
            int phiIndex = loopBeginNode.phiPredecessorIndex(node);
            System.out.println("The phi index for the loopBegin node following: " +  node + " is " + phiIndex);
            mergeIndexMapping.put(loopBeginNode, phiIndex);

            loopBeginNode.accept(this);
            return null;
        }

        private void applyMerge(AbstractMergeNode node){
            // Gets the index from which this merge node was reached.
            int accessIndex = mergeIndexMapping.get(node);
            // Get all associated phi nodes of this merge node
            // Evaluate all associated phi nodes with merge access index as their input
            System.out.println("Evaluating phis for " + node + " at index " + accessIndex);
            for (Node phi: node.phis()) {
                System.out.println("---- Start " + phi + " Evaluation ------");
                RuntimeType phiVal = ((PhiNode) phi).valueAt(accessIndex).accept(new DataFlowVisit());
                System.out.println("---- End " + phi + " Evaluation ------");
                state.put(phi, phiVal);
            }
        }

        public RuntimeType visit(MergeNode node) {
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            // Evaluates the phis associated with the given merge node.
            applyMerge(node);
            // Continue to following cfg node
            node.next().accept(this);
            return null;
        }

        public RuntimeType visit(LoopBeginNode node) {
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            // Evaluates the phis associated with the given merge node.
            applyMerge(node);
            // Continue to following cfg node
            node.next().accept(this);
            return null;
        }

        @Override //todo
        public RuntimeType visit(LoopExitNode node) {
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            node.next().accept(this);
            return null;
        }

        public RuntimeType visit(ValueProxyNode node) {
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(StoreFieldNode node){
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");

            System.out.printf("Storing value: %s in %s - specifically in offset  %s\n", node.value(), node.field(), node.field().getOffset());

            RuntimeType value = node.value().accept(new DataFlowVisit());
            offsetMapping.put(node.field().getOffset(), value);

            node.next().accept(this); //as opposed to: (compile time typed) visit(node.next());
            return null;
        }

        public RuntimeType visit(LoadFieldNode node){
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            // Execute LoadFieldNode eagerly as control flow edges are traversed
            // Assign value from offset to node. - may be null
            int offset = node.field().getOffset();
            RuntimeType valueAtOffset = offsetMapping.get(offset);

            state.put(node, valueAtOffset);

            node.next().accept(this);
            return null;
        }

        public RuntimeType visit(AddNode node) {
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(IfNode node) {
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");

            RuntimeType condition = node.condition().accept(new DataFlowVisit());

            if (condition.getBoolean()){
                System.out.println("The condition evaluated to true");
                node.trueSuccessor().accept(this);
            } else {
                System.out.println("The condition evaluated to False");
                node.falseSuccessor().accept(this);
            }

            return null;
        }

        public RuntimeType visit(IntegerLessThanNode node) {
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }


        public RuntimeType visit(ParameterNode node) {
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(ReturnNode node) {
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            if (node.result() != null){ // May have return node with no associated result ValueNode
                RuntimeType out = node.result().accept(new DataFlowVisit());
                state.put(node, out);
            }
            
            return null;
        }

        public RuntimeType visit(ConstantNode node) {
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(ValuePhiNode node) {
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }
    }

    //////////////////////////// Data Flow Visit Methods ///////////////////////////////////////////

    public class DataFlowVisit implements NodalVisitor {
        public RuntimeType visit(ValueNode node) {
            System.out.println("Encountered DATA base case: " + node + node.getClass() + " (node type not handled yet!)\n");

            return null;
        }

        public RuntimeType visit(StartNode node) {
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");

            return null;
        }

        public RuntimeType visit(BeginNode node) {
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");

            return null;
        }

        public RuntimeType visit(EndNode node) {
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");

            return null;
        }

        public RuntimeType visit(StoreFieldNode node) {
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");

            return null;
        }

        public RuntimeType visit(LoadFieldNode node){
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            RuntimeType value = state.get(node);

            if (value != null){
                return value;
            } else {
                System.out.printf("No data stored in field - returning GARBAGE from %s\n\n", node.field().getOffset());
                errorMessages.add( String.format("Load from field without stored value (ID: %s, Field Offset: %s)", node.id(), node.field().getOffset()));
                return new RTInteger(1);
            }
        }

        public RuntimeType visit(AddNode node) {
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");

            RuntimeType x_value = node.getX().accept(this);
            RuntimeType y_value = node.getY().accept(this);

            if (x_value == null || y_value == null){
                errorMessages.add( String.format("Invalid inputs to addNode (ID: %s): %s %s", node.id(), x_value, y_value));
            }
            assert x_value != null;
            assert y_value != null;

            RuntimeType result = x_value.add(y_value);
            state.put(node, result);
            return result;
        }

        public RuntimeType visit(IfNode node) {
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        @Override
        public RuntimeType visit(IntegerLessThanNode node) {
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");

            RuntimeType a = node.getX().accept(this);
            RuntimeType b = node.getY().accept(this);

            System.out.println("Value of a was " + a);
            System.out.println("Value of b was " + b);

            assert a != null;
            assert b != null;

            RuntimeType out = a.lessThan(b);
            System.out.println("a < b is : " + out);

            return out;
        }

        @Override
        public RuntimeType visit(ParameterNode node) {
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            //todo
            System.out.println("TODO implement parameter data retrieval: Generating fake data");
            return new RTInteger(2);
        }

        @Override
        //todo
        public RuntimeType visit(LoopBeginNode node) {
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        @Override //todo
        public RuntimeType visit(LoopEndNode node) {
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        @Override //todo
        public RuntimeType visit(LoopExitNode node) {
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        @Override //todo
        public RuntimeType visit(ValueProxyNode node) {
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return node.getOriginalNode().accept(this);
        }

        public RuntimeType visit(ReturnNode node) {
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(ConstantNode node){
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return new RTInteger(((JavaConstant) node.getValue()).asInt());
        }

        public RuntimeType visit(MergeNode node) {
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(ValuePhiNode node) {
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");
//            System.out.println("The whole proper state is currently: " + state + " (from inside visit " + node + ")");
            return state.get(node);
        }
    }
}