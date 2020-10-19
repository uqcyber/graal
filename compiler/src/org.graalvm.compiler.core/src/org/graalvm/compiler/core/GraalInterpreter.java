package org.graalvm.compiler.core;


import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.core.common.InterpreterFrame;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


// todo ensure we are working with the 1st graph! (initial "After Parsing" graph state)
//  -> to avoid machine specific optimisations.

//todo use evaluationVisitor pattern!
//todo look into extending phases?
//todo look into GraalCompilerTest for some interesting test methods e.g. parseEager()

public class GraalInterpreter {
    /**
     * Evaluate all of the floating nodes / input nodes to values then supply these to the fixed nodes.
     * Should have a visit method for almost every single type of possible instantiated *leaf* node
     * e.g. No FixedNode but would have a visit method for IfNode.
     *
     * todo Use annotations to indicate non leaf visit methods
     * todo some form of dynamic casting to actual class type
     * todo use node.getClass() or node.getNodeClass().getJavaClass()) and combine with dispatch map or use interface
     *
     */

    private static class NodeVisitor {
        private Map<Integer, Object> state = new HashMap<>();
        private Map<Class<?>, Runnable> dispatch = new HashMap<>();

        InterpreterFrame frame = new InterpreterFrame(0);

        public NodeVisitor(){
            setDispatch();
        }

        private void setDispatch() {
            // Using a lambda expression todo
            dispatch.put(ReturnNode.class, () -> System.out.println("Replace with visit call..."));
        }


        public void dispatchChoice(Node node){
            Class<? extends Node> currentNodeClass = node.getClass();
            if (dispatch.containsKey(currentNodeClass)){
                Runnable x = dispatch.get(currentNodeClass);
                x.run();
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

        public Object visit(ValueNode node){
            System.out.println("Of type Value Node, deciding subclass:");

            if (node instanceof FixedNode){
                return visit((FixedNode) node);
            }
            else if (node instanceof FloatingNode) {
                return visit((FloatingNode) node);
            }

            System.err.println("Unhandled visit for: " + node.toString() + " of class " + node.getClass());
            return null;
        }

        public Object visit(FloatingNode node){
            System.out.println("Of type FloatingNode, deciding subclass: (Actually)" + node.getClass());
            if (node instanceof ConstantNode){
                return visit((ConstantNode) node);
            }

            else if (node instanceof AddNode){
                return visit((AddNode) node);
            }

            else if (node instanceof ParameterNode){
                return visit((ParameterNode) node);
            }

            return null;
        }

        /**
         * Entry point for most graphs.
         */
        public Object visit(StartNode node) {
            System.out.println("Visiting " + node.getNodeClass().shortName());

            node.execute(frame);

            return visit(node.next());
        }

        public Object visit(FixedNode node) {
            System.out.println("Deciding subclass of fixed node:");
            if (node instanceof ControlSinkNode){
                return visit((ControlSinkNode) node);
            } else if (node instanceof LoadFieldNode){
                return visit((LoadFieldNode) node);
            }
            return null;
        }

        public Object visit(LoadFieldNode node){
            System.out.println("Visiting Load Field Node");
            System.out.println("The value of the field node is " + node.field());

            state.put(1,  node.field()); // store value in variable. - todo base on node id or similar!

            System.out.println("State is currently " + state);

            return visit(node.next());
        }

        public Object visit(ControlSinkNode node){
            if (node instanceof ReturnNode){
                return visit((ReturnNode) node);
            }
            return null;
        }

        public Object visit(ReturnNode node) {
            System.out.println("Visiting " + node.getNodeClass().shortName());
            // todo using -1 as signifier for return -> should use unique node id.
            state.put(-1, visit(node.result()));
            return null;
        }

        public Object visit(AddNode node){
            System.out.println("Visiting " + node.getNodeClass().shortName());
            //todo check if leafID is the expected unique identifier

//            visit(node.getX()) + visit(node.getY())

            Node nodeX = node.getX();
            Node nodeY = node.getY();

            Object x = null;
            Object y = null;


            if (!state.containsKey(1)){
                x = visit(node.getX());
            } else {
                x = state.get(1); //todo uncouple outputs / use proper keys.
            }

            if (!state.containsKey(1)){
                y = visit(node.getY());
            } else {
                y = state.get(1);
            }

            if (x != null && y != null){
                System.out.println("Class of x is " + x.getClass() + " CLass of y is " + y.getClass());
            }
//            state.put(node.getNodeClass().getLeafId(), visit(node.getX()));
            //todo return sum of values
            return x;
        }

        public Object visit(ConstantNode node) {
            System.out.println("Visiting constant node");
            System.out.println("Value was " + node.asConstant().toValueString());
            return node.getValue();
        }

        public Object visit(ParameterNode node){
            System.out.println("Visiting " + node.getNodeClass().shortName());
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


            // Based on LIR Generator: fetches all param nodes in graph

//            Value[] params = new Value[incomingArguments.getArgumentCount()];

//            NodeIterable<ParameterNode> allParams = node.graph().getNodes(ParameterNode.TYPE);
//            Value paramValue = new Value(); //params[index];

            return 5;
//            return paramValue;
        }

        public Map<Integer, Object> getState(){
            return state;
        }
    }

    /*
        perform operation,
        'store data' from operation,
        continue control flow?
     */
    public static void executeGraph(StructuredGraph graph){

        System.out.println("Now executing graph");

        // todo Worklist should only needed for small step semantics todo

        NodeVisitor nv = new NodeVisitor();
        nv.visit(graph.start());


        System.out.println(nv.getState());
        System.out.println("Return was: " + nv.getState().get(-1));
    }

    public static void outputGraphInfo(StructuredGraph graph){

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

        System.out.println("Iterating over framestate nodes.");
        for (FrameState node : graph.getNodes(FrameState.TYPE)){
            for (ValueNode val: node.values()){
                System.out.println(val);
            }
        }

        System.out.println("State after is " + start.stateAfter());
        System.out.println("Usages of start: " + start.usages());

        System.out.println("-----------------------------------------------\n\n");
    }
}