package org.graalvm.compiler.core;


import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
//import org.graalvm.compiler.core.common.InterpreterFrame;
import org.graalvm.compiler.nodes.NodalVisitor;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;

import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.RuntimeType;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


/**
 * Evaluate all of the floating nodes / input nodes to values then supply these to the fixed nodes.public void accept(NodalVisitor v){
        v.visit(this);
    }
 * Should have a visit method for almost every single type of possible instantiated *leaf* node
 * e.g. No FixedNode but would have a visit method for IfNode.
 *
 * todo Use annotations to indicate non leaf visit methods
 * todo some form of dynamic casting to actual class type
 * todo use node.getClass() or node.getNodeClass().getJavaClass()) and combine with dispatch map or use interface
 * todo implement specific visitors and visit pattern e.g. visitStartNode()
 *
 */
public class GraalInterpreter{ //implements NodeVisitor {
    //private InterpreterFrame currentFrame;
    private Map<Integer, Object> state = new HashMap<>(); // From Offset -> Value
    private Map<Node, Object> nodeMapping = new HashMap<>(); // From Node -> Value
    private ArrayList<String> errorMessages = new ArrayList<>();

    public Map<Integer, Object> getState(){
        return state;
    }

    public void executeGraph(StructuredGraph graph) throws Exception{
        //todo set up stack and heap
        System.out.println("Now executing graph\n");
        //currentFrame = new InterpreterFrame(0);

        ControlFlowVisit controlVisit = new ControlFlowVisit();
//        controlVisit.visit(graph.start());
        graph.start().accept(controlVisit);

//        visitOLD(graph.start());
        if (state.containsKey(-2)) {
            System.out.printf("The following issues were encountered when interpreting the graph: %s\n", state.get(-2));
        } else {
            //System.out.printf("The return value was: %d\n", (int) state.get(-1));  //previously assumed int return objects
            System.out.printf("The return value was: %s\n", state.get(-1));
        }
    }



    // Represents a runtime Integer Object
    class RTInteger extends RuntimeType {

        protected int value;

        public RTInteger(int asInt) {
            super();
            value = asInt;
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
            System.out.println("Encountered CONTROL base case: " + node + " (node type not handled yet!)\n");
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
            return null;
        }

        public RuntimeType visit(EndNode node){
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            return null;
        }

        public RuntimeType visit(StoreFieldNode node){
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");

            System.out.printf("Storing value: %s in %s - specifically in offset  %s\n", node.value(), node.field(), node.field().getOffset());
            state.put(node.field().getOffset(),  node.value());

            node.next().accept(this); //as opposed to: (compile time typed) visit(node.next());
            return null;
        }

        public RuntimeType visit(LoadFieldNode node){
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");
            // Execute Load node eagerly as control flow edges are traversed, stores offset of load  in a Map
            // stores Null if no valid value is mapped in state map
            nodeMapping.put(node, getValueFromOffset(node.field().getOffset())); // may be null

            node.next().accept(this);
            return null;
        }

        public RuntimeType visit(ReturnNode node) {
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");

            //state.put(-1, visitOLD(node.result()));
            RuntimeType out = node.result().accept(new DataFlowVisit());
            state.put(-1, out);
            return null;
        }

        public RuntimeType visit(ConstantNode node) {
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");

            return null;
        }

        //todo
        public RuntimeType visit(MergeNode node) {
            System.out.println("Visiting CONTROL " + node.getNodeClass().shortName() + "\n");

            node.next().accept(this);

            return null;
        }

        public RuntimeType visit(ValuePhiNode node) {
            return null;
        }


    }

    //////////////////////////// Data Flow Visit Methods ///////////////////////////////////////////

    public class DataFlowVisit implements NodalVisitor {
        public RuntimeType visit(ValueNode node) {
            System.out.println("Encountered DATA base case: " + node + " (node type not handled yet!)\n");

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

            //todo replace / removeNodeMapping.
            //currently returns false values:
            return new RTInteger(1);
//            RuntimeType value = nodeMapping.get(node);
//            if (value != null){
//                return value;
//            } else {
//                System.out.printf("No data stored in field - returning GARBAGE from %s\n\n", node.field().getOffset());
//                state.put(-2, errorMessages);
//                errorMessages.add( String.format("Load from field without stored value (ID: %s, Field Offset: %s)", node.id(), node.field().getOffset()));
//                return 1;
//            }
        }

        public RuntimeType visit(ReturnNode node) {
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");

            return null;
        }

        public RuntimeType visit(ConstantNode node){
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");

            return new RTInteger(((JavaConstant)node.getValue()).asInt());
        }

        public RuntimeType visit(MergeNode node) {
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");

            return null;
        }

        public RuntimeType visit(ValuePhiNode node) {
            System.out.println("Visiting DATA " + node.getNodeClass().shortName() + "\n");

            return null;
        }
    }








    // Below is the previously used concept for traversal: Deprecated and replaced with the modified visitor method.

    // Global / Most generic visit pattern
    public Object visitOLD(Node node) {
        if (node instanceof ValueNode){
            return visitOLD((ValueNode) node);
        }
        System.err.println("Unhandled visit for: " + node.toString() + " of class " + node.getClass());
        return null;
    }

    // Determines node subclass for control flow visit
    public Object visitOLD(ValueNode node){
//        System.out.println("Of type ValueNode, deciding subclass: (Actually)" + node.getClass() + "\n");

        if (node instanceof FixedNode){
            return visitOLD((FixedNode) node);
        }
        else if (node instanceof FloatingNode) {
            return visitOLD((FloatingNode) node);
        }
        System.err.println("value: Unhandled visit for: " + node.toString() + " of class " + node.getClass());
        return null;
    }

    // Determines node subclass for data flow visit
    public Object visitDataOLD(ValueNode node){
        if (node instanceof LoadFieldNode){
            return visitDataOLD((LoadFieldNode) node);
        } else if (node instanceof ConstantNode) {
            return visitDataOLD((ConstantNode) node);
        } else if (node instanceof AddNode){
            return visitDataOLD((AddNode) node);
        } else if (node instanceof ParameterNode){
            return visitDataOLD((ParameterNode) node);
        }
        //todo add other subclasses
        System.err.println("data: Unhandled visit for: " + node.toString() + " of class " + node.getClass());
        return null;
    }

    // todo check if all floating nodes can be visited for data flow
    public Object visitOLD(FloatingNode node){
//        System.out.println("Of type FloatingNode, deciding subclass: (Actually)" + node.getClass() + "\n");
        if (node instanceof ConstantNode){
            return visitDataOLD((ConstantNode) node);
        } else if (node instanceof AddNode){
            return visitDataOLD((AddNode) node);
        } else if (node instanceof ParameterNode){
            return visitOLD((ParameterNode) node);
        } else if (node instanceof ValuePhiNode){
            return visitOLD((ValuePhiNode) node);
        }

        System.err.println("floating: Unhandled visit for: " + node.toString() + " of class " + node.getClass());
        return null;
    }

    public Object visitOLD(FixedNode node) {
//        System.out.println("Of type FixedNode, deciding subclass: (Actually)" + node.getClass() + "\n");

        if (node instanceof ControlSinkNode){
            return visitOLD((ControlSinkNode) node);
        } else if (node instanceof LoadFieldNode){
            return visitOLD((LoadFieldNode) node);
        } else if (node instanceof IfNode){
            return visitOLD((IfNode) node);
        } else if (node instanceof BeginNode){
            return visitOLD((BeginNode) node);
        } else if (node instanceof EndNode){
            return visitOLD((EndNode) node);
        } else if (node instanceof MergeNode){
            return visitOLD((MergeNode) node);
        } else if (node instanceof StoreFieldNode){
            return visitOLD((StoreFieldNode) node);
        }

        System.err.println("fixed: Unhandled visit for: " + node.toString() + " of class " + node.getClass());
        return null;
    }

    /**
     * Entry point for most graphs.
     */
    public Object visitOLD(StartNode node) {
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");

        return visitOLD(node.next());
    }

    public Object visitOLD(BeginNode node) {
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");

        return visitOLD(node.next());
    }

    public Object visitOLD(EndNode node) {
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");

        //todo add case for multiple successors.
        for (Node mergeNode : node.cfgSuccessors()){
            return visitOLD(mergeNode); //todo currently just visits first cfgSuccessor
        }

        return null;
    }

    public Object visitOLD(MergeNode node){
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");

        return visitOLD(node.next());
    }

    public Object visitOLD(ValuePhiNode node){
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");

        return visitOLD(node.firstValue());
    }

    public Object visitDataOLD(ParameterNode node){
        System.out.println("(Data) Executing (Getting value from) " + node.getNodeClass().shortName());
        Object value = nodeMapping.get(node);
        if (value != null){
            return value;
        } else {
            System.out.printf("No data stored in field - returning GARBAGE from %s\n\n", node.id());
            state.put(-2, errorMessages);
            errorMessages.add( String.format("Load from parameter without stored value %s", node.id()));
            return 1;
        }
    }



    public Object visitOLD(LogicNode node){
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");

        // todo generalise type e.g. LogicNode -> BinaryOpLogicNode -> CompareNode -> IntegerLowerThanNode -> IntegerLessThanNode
        if (node instanceof IntegerLessThanNode){
            System.out.println("must be < node");
            IntegerLessThanNode x  = (IntegerLessThanNode) node;
            ValueNode a = x.getX();
            ValueNode b = x.getY();

            System.out.println("Calc value of a was: " + visitDataOLD(x.getX()));
            System.out.println("The value of a is " + a);
            System.out.println("The value of b is " + b);
        }

        //todo
        return true;
    }

    public Object visitOLD(IfNode node){
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");
        LogicNode condition = node.condition();
        AbstractBeginNode trueSucc = node.trueSuccessor();
        AbstractBeginNode falseSucc = node.falseSuccessor();

        boolean isTrue = (boolean) visitOLD(condition);

        if (isTrue){
            System.out.println("The condition evaluated to true");
            return visitOLD(trueSucc);
        }
        System.out.println("The condition evaluated to False");
        return visitOLD(falseSucc);
    }

    // todo rework to attempt to convert any type of node stored at offset to an int.
    public Number getValueFromOffset(int offset) {
        Object valueAtOffset = state.get(offset);

        if (valueAtOffset == null){
            return null;
        }
        // Converts value to int from ConstantNode if possible
        if (valueAtOffset instanceof ConstantNode) {
            ConstantNode storedNode = (ConstantNode) valueAtOffset;
            return ((JavaConstant) (storedNode.getValue())).asInt();
        } else {
            //todo Handle non constant values.
            return null;
        }
    }

    public Object visitOLD(LoadFieldNode node){
        System.out.println("(Control) Executing " + node.getNodeClass().shortName());
        // Execute Load node eagerly as control flow edges are traversed, stores offset of load  in a Map
        // stores Null if no valid value is mapped in state map
        nodeMapping.put(node, getValueFromOffset(node.field().getOffset())); // may be null
        return visitOLD(node.next());
    }

    public Object visitDataOLD(LoadFieldNode node){
        System.out.println("(Data) Executing (Getting value from) " + node.getNodeClass().shortName());
        Object value = nodeMapping.get(node);
        if (value != null){
            return value;
        } else {
            System.out.printf("No data stored in field - returning GARBAGE from %s\n\n", node.field().getOffset());
            state.put(-2, errorMessages);
            errorMessages.add( String.format("Load from field without stored value (ID: %s, Field Offset: %s)", node.id(), node.field().getOffset()));
            return 1;
        }
    }

    public Object visitOLD(StoreFieldNode node){
        System.out.println("Visiting " + node.getNodeClass().shortName());

        System.out.printf("Storing value: %s in %s - specifically in offset  %s\n", node.value(), node.field(), node.field().getOffset());
        state.put(node.field().getOffset(),  node.value());

        return visitOLD(node.next());
    }

    public Object visitOLD(ControlSinkNode node){
        if (node instanceof ReturnNode){
            return visitOLD((ReturnNode) node);
        }
        return null;
    }

    public Object visitOLD(ReturnNode node) {
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");
        // todo using -1 as signifier for return -> should use unique node id.
        state.put(-1, visitOLD(node.result()));
        return null;
    }

    // todo  consider other options to differentiate between retrieving a value (data flow) rather than control flow
    // options: multiple return values
    // alternate access method signatures. e.g. visit(LoadFieldNode, boolean asInput)
    // Stack based?

    public Object visitDataOLD(AddNode node){
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");

        ValueNode nodeX = node.getX();
        ValueNode nodeY = node.getY();
        Object x = visitDataOLD(nodeX); // todo deal with other input value nodes
        Object y = visitDataOLD(nodeY);

        int sum = 0;

        if (x != null && y != null){
            int xint = (int)x;
            int yint = (int)y;
            sum = xint + yint;
        }
        nodeMapping.put(node, sum);
        return sum;
    }

    public Object visitDataOLD(ConstantNode node) {
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");

        //todo ensure not null/is int
        return ((JavaConstant)node.getValue()).asInt();
    }

    public Object visitOLD(ParameterNode node){
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");
        //todo get the value stored in the parameter node. Consider index position?

        System.out.println("Creation position: " + node.getCreationPosition());
        System.out.println("insertion position: " + node.getInsertionPosition());
        System.out.println("node source position: " + node.getNodeSourcePosition());
        System.out.println("java kind: " + node.getStackKind());
        System.out.println("debug>" + node.getDebugProperties());
        System.out.println("index " + node.index());
        System.out.println("Is node constant?" + node.isJavaConstant());

        //todo access framestate node to extract values?
        NodeIterable<FrameState> out = node.graph().getNodes(FrameState.TYPE);

        for (FrameState fr : out){
            System.out.println("Framestate node" + fr);
        }

        // The index of the current parameter node in the current method
        int index = node.index();

        ResolvedJavaMethod method = node.graph().method();
        ResolvedJavaMethod.Parameter[] parameters = node.graph().method().getParameters();
        System.out.println("The parameters are: " + Arrays.toString(parameters));

        return 5;
//            return paramValue;
    }


    public void outputGraphInfo(StructuredGraph graph){
        System.out.println("Now outputting Graph Info\n-------------------------------------");

        StartNode start = graph.start();

        System.out.println("Predecessors:" + start.cfgPredecessors());
        System.out.println("Successors: " + start.cfgSuccessors());
        System.out.println("The inputs of start are " + start.inputs());
        System.out.println("The successors of start are " + start.successors());

        FixedNode current_node = start.next();
        System.out.println("the next node is " + current_node);

        System.out.println("Get nodes gives");
        for (Node node : graph.getNodes()) {
            System.out.println(node);
        }

        System.out.println("State after is " + start.stateAfter());

        System.out.println("-----------------------------------------------\n\n");
    }
}