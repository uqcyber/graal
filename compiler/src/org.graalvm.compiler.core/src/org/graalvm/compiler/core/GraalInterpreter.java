package org.graalvm.compiler.core;


import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
//import org.graalvm.compiler.core.common.InterpreterFrame;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
//import org.graalvm.compiler.interpreter.NodeVisitor; todo

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


/*
enum errorCodes {
    LOAD_ERROR
}
// todo ensure we are working with the 1st graph! (initial "After Parsing" graph state)
//  -> to avoid machine specific optimisations.

//todo use evaluationVisitor pattern!
//todo look into extending phases?
//todo look into GraalCompilerTest for some interesting test methods e.g. parseEager()

//todo add a class for error output messages
class ErrorReporter{
    private static final Map<errorCodes, String> errorDict;

    static{
        errorDict =   new HashMap<errorCodes, String>() {{
            put(errorCodes.LOAD_ERROR, "Loading from field with no assigned values");
//            put(1, "Loading from field with no assigned values");
        }};
    }

    public String getErrorCodes(ArrayList<Integer> errorCodes){
        ArrayList<String> messages = new ArrayList<String>();
        for(int elem: errorCodes) {
            String errorMessage = errorDict.getOrDefault(elem, "");
            messages.add(errorMessage);
        }

        return messages.toString();
    }
}
 */

/**
 * Evaluate all of the floating nodes / input nodes to values then supply these to the fixed nodes.
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

        visit(graph.start());

        if (state.containsKey(-2)) {
            System.out.printf("The following issues were encountered when interpreting the graph: %s\n", state.get(-2));
        } else {
            System.out.printf("The return value was: %d\n", (int) state.get(-1));
        }
    }

    // Global / Most generic visit pattern
    // todo could act as a sort of switch for visiting nodes (down casts based on runtime instance type)
    public Object visit(Node node) {
        if (node instanceof ValueNode){
            return visit((ValueNode) node);
        }

        System.err.println("Unhandled visit for: " + node.toString() + " of class " + node.getClass());
        return null;
    }

    // Determines node subclass for control flow visit
    public Object visit(ValueNode node){
//        System.out.println("Of type ValueNode, deciding subclass: (Actually)" + node.getClass() + "\n");

        if (node instanceof FixedNode){
            return visit((FixedNode) node);
        }
        else if (node instanceof FloatingNode) {
            return visit((FloatingNode) node);
        }
        System.err.println("value: Unhandled visit for: " + node.toString() + " of class " + node.getClass());
        return null;
    }

    // Determines node subclass for data flow visit
    public Object visitData(ValueNode node){
        if (node instanceof LoadFieldNode){
            return visitData((LoadFieldNode) node);
        } else if (node instanceof ConstantNode) {
            return visitData((ConstantNode) node);
        } else if (node instanceof AddNode){
            return visitData((AddNode) node);
        } else if (node instanceof ParameterNode){
            return visitData((ParameterNode) node);
        }
        //todo add other subclasses
        System.err.println("data: Unhandled visit for: " + node.toString() + " of class " + node.getClass());
        return null;
    }

    // todo check if all floating nodes can be visited for data flow
    public Object visit(FloatingNode node){
//        System.out.println("Of type FloatingNode, deciding subclass: (Actually)" + node.getClass() + "\n");
        if (node instanceof ConstantNode){
            return visitData((ConstantNode) node);
        } else if (node instanceof AddNode){
            return visitData((AddNode) node);
        } else if (node instanceof ParameterNode){
            return visit((ParameterNode) node);
        } else if (node instanceof ValuePhiNode){
            return visit((ValuePhiNode) node);
        }

        System.err.println("floating: Unhandled visit for: " + node.toString() + " of class " + node.getClass());
        return null;
    }

    public Object visit(FixedNode node) {
//        System.out.println("Of type FixedNode, deciding subclass: (Actually)" + node.getClass() + "\n");

        if (node instanceof ControlSinkNode){
            return visit((ControlSinkNode) node);
        } else if (node instanceof LoadFieldNode){
            return visit((LoadFieldNode) node);
        } else if (node instanceof IfNode){
            return visit((IfNode) node);
        } else if (node instanceof BeginNode){
            return visit((BeginNode) node);
        } else if (node instanceof EndNode){
            return visit((EndNode) node);
        } else if (node instanceof MergeNode){
            return visit((MergeNode) node);
        } else if (node instanceof StoreFieldNode){
            return visit((StoreFieldNode) node);
        }

        System.err.println("fixed: Unhandled visit for: " + node.toString() + " of class " + node.getClass());
        return null;
    }

    /**
     * Entry point for most graphs.
     */
    public Object visit(StartNode node) {
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");

        return visit(node.next());
    }

    public Object visit(BeginNode node) {
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");

        return visit(node.next());
    }

    public Object visit(EndNode node) {
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");

        //todo add case for multiple successors.
        for (Node mergeNode : node.cfgSuccessors()){
            return visit(mergeNode); //todo currently just visits first cfgSuccessor
        }

        return null;
    }

    public Object visit(MergeNode node){
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");

        return visit(node.next());
    }

    public Object visit(ValuePhiNode node){
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");

        return visit(node.firstValue());
    }

    public Object visitData(ParameterNode node){
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



    public Object visit(LogicNode node){
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");

        // todo generalise type e.g. LogicNode -> BinaryOpLogicNode -> CompareNode -> IntegerLowerThanNode -> IntegerLessThanNode
        if (node instanceof IntegerLessThanNode){
            System.out.println("must be < node");
            IntegerLessThanNode x  = (IntegerLessThanNode) node;
            ValueNode a = x.getX();
            ValueNode b = x.getY();

            System.out.println("Calc value of a was: " + visitData(x.getX()));
            System.out.println("The value of a is " + a);
            System.out.println("The value of b is " + b);
        }

        //todo
        return true;
    }

    public Object visit(IfNode node){
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");
        LogicNode condition = node.condition();
        AbstractBeginNode trueSucc = node.trueSuccessor();
        AbstractBeginNode falseSucc = node.falseSuccessor();

        boolean isTrue = (boolean) visit(condition);

        if (isTrue){
            System.out.println("The condition evaluated to true");
            return visit(trueSucc);
        }
        System.out.println("The condition evaluated to False");
        return visit(falseSucc);
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

    public Object visit(LoadFieldNode node){
        System.out.println("(Control) Executing " + node.getNodeClass().shortName());
        // Execute Load node eagerly as control flow edges are traversed, stores offset of load  in a Map
        // stores Null if no valid value is mapped in state map
        nodeMapping.put(node, getValueFromOffset(node.field().getOffset())); // may be null
        return visit(node.next());
    }

    public Object visitData(LoadFieldNode node){
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

    public Object visit(StoreFieldNode node){
        System.out.println("Visiting " + node.getNodeClass().shortName());

        System.out.printf("Storing value: %s in %s - specifically in offset  %s\n", node.value(), node.field(), node.field().getOffset());
        state.put(node.field().getOffset(),  node.value());

        return visit(node.next());
    }

    public Object visit(ControlSinkNode node){
        if (node instanceof ReturnNode){
            return visit((ReturnNode) node);
        }
        return null;
    }

    public Object visit(ReturnNode node) {
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");
        // todo using -1 as signifier for return -> should use unique node id.
        state.put(-1, visit(node.result()));
        return null;
    }

    // todo  consider other options to differentiate between retrieving a value (data flow) rather than control flow
    // options: multiple return values
    // alternate access method signatures. e.g. visit(LoadFieldNode, boolean asInput)
    // Stack based?

    public Object visitData(AddNode node){
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");

        ValueNode nodeX = node.getX();
        ValueNode nodeY = node.getY();
        Object x = visitData(nodeX); // todo deal with other input value nodes
        Object y = visitData(nodeY);

        int sum = 0;

        if (x != null && y != null){
            int xint = (int)x;
            int yint = (int)y;
            sum = xint + yint;
        }
        nodeMapping.put(node, sum);
        return sum;
    }

    public Object visitData(ConstantNode node) {
        System.out.println("Visiting " + node.getNodeClass().shortName() + "\n");

        //todo ensure not null/is int
        return ((JavaConstant)node.getValue()).asInt();
    }

    public Object visit(ParameterNode node){
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