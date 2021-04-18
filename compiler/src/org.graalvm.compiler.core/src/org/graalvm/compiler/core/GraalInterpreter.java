package org.graalvm.compiler.core;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
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
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.SignedDivNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.UnsignedRightShiftNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;

// Custom Runtime Types
import org.graalvm.compiler.core.runtimetypes.RTInteger;
import org.graalvm.compiler.core.runtimetypes.RTVoid;
import org.graalvm.compiler.core.runtimetypes.RTArray;

import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.phases.tiers.HighTierContext;

// Reflection methods for dispatch without Visitor pattern
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;


public class GraalInterpreter {
    private final GraalInterpreter interpreter = this;
    private boolean logging;
    private final HighTierContext context;
    private int indent = -1; // Used for log output levels (to clearly show nested function calls)
    private final ArrayList<String> errorMessages = new ArrayList<>();

    private final Map<Integer, RuntimeType> offsetMapping = new HashMap<>(); //data offsets to values stored todo deprecate
    private final Map<Node, Integer>  mergeIndexMapping = new HashMap<>(); // for Merge Nodes to 'remember' which Phi to eval
    private final ActivationStack activationStack = new ActivationStack(); //invokes push, return (from execution) pops
    private final Map<Node, RuntimeType> state = new HashMap<>(); // acts as Heap

    public GraalInterpreter(HighTierContext context, boolean shouldLog){
        this.activationStack.push(new ActivationRecord());
        this.logging = shouldLog;
        this.context = context;
    }

    private void log(String input){
        if (logging) {
            String spaces = new String(new char[indent*4]).replace("\0", " ");
            System.err.println(spaces + input);
        }
    }

    public void setLogging(boolean shouldLog){
        logging = shouldLog;
    }

    //todo check if interpreter execution should return anything, e.g. list of steps taken, final runtime type?
    public RuntimeType executeGraph(StructuredGraph graph){ //todo throw exception?
        indent = indent + 1;
        log("-------------------------------Now executing graph-----------------------------\n");
        ControlFlowVisit controlVisit = new ControlFlowVisit();
        execute(controlVisit, graph.start());

        if (!errorMessages.isEmpty()){
            System.err.println("Encountered the following errors: ");
            for (String err : errorMessages) {
                log(err);
            }
            return null;
        }
        log(String.format("The return value was: %s\n", activationStack.peek().get_return()));
//        log(String.format("Current state is: %s\n Current Stack is %s", state, activationStack));
        log("-------------------------------End graph execution---------------------------\n");
        indent = indent - 1;
        return activationStack.pop().get_return();
    }

    private class ActivationStack { //todo add error handling, variables and object references
        private final ArrayList<ActivationRecord> stack = new ArrayList<>();

        public ActivationRecord pop(){ return stack.remove(0); }

        public void push(ActivationRecord activationRecord){
            activationRecord.set_depth(stack.size());
            stack.add(0, activationRecord);
        }

        // Adds a 'reference' to data object in top activation record (actually just stores a reference to node)
        public void addLocalVar(Node node){
            this.peek().addLocalVar(node);
        }

        public ActivationRecord peek(){ return stack.get(0); }

        public String toString() { return "ActivationStack{" + "stack=" + stack + '}'; }
    }

    private class ActivationRecord {
        private final ArrayList<RuntimeType> evaluatedParameters;
        private final ArrayList<ValueNode> originalArguments;
        private final ArrayList<Node> localVariables; // todo currently unused...
//        private final InvokeNode activationNode;
        private RuntimeType returnValue = null;
        private int depth; //todo consider

        // Creates an activation record from an invoke node
        public ActivationRecord(InvokeNode node){
            CallTargetNode callNode = node.callTarget();
            localVariables = new ArrayList<>();
            evaluatedParameters = new ArrayList<>();
            originalArguments = new ArrayList<>(Arrays.asList(callNode.arguments().toArray(new ValueNode[0])));

            log(String.format("Supplied args are %s", callNode.arguments()));

            // Evaluates each of the given parameters
            for (ValueNode arg : originalArguments) {
                RuntimeType argVal = execute(new DataFlowVisit(), arg);
                evaluatedParameters.add(argVal);
            }
        }

        // Creates empty activation record
        public ActivationRecord(){
            evaluatedParameters = new ArrayList<>();
            originalArguments = new ArrayList<>();
            localVariables = new ArrayList<>();
        }

        public void addLocalVar(Node node){
            localVariables.add(node);
        }

        public void set_depth(int depth){ this.depth = depth; }

        public void set_return(RuntimeType output){ returnValue = output; }

        public RuntimeType get_return(){ return returnValue; }

        public ArrayList<RuntimeType> getEvaluatedParameters(){ return evaluatedParameters; }

        public String toString(){
            return String.format("(Depth: %s) Activation Record: Parameters: %s", depth, evaluatedParameters);
        }
    }

    private RuntimeType execute(NodalVisitor visitor, Node node) {
        // todo cache classType to Method results in mapping
        // Using generic 'unbounded wildcard' as the type fo the node can be any subclass of Object
        Class<?>[] args = new Class<?>[1];
        args[0] = node.getClass();
        Method matchingMethod;

        // Determine visitor type, then get corresponding method
        String typeOfTraversal = "CONTROL";
        try {
            if (visitor instanceof ControlFlowVisit){
                matchingMethod = ControlFlowVisit.class.getMethod("visit", args);
            } else {
                matchingMethod = DataFlowVisit.class.getMethod("visit", args);
                typeOfTraversal = "DATA";
            }
        } catch (NoSuchMethodException e) {
            errorMessages.add(String.format("UNIMPLEMENTED CASE: Encountered %s %s\n", typeOfTraversal, node.getNodeClass().shortName()));
//            e.printStackTrace();
            return null;
        }

        try{
            log(String.format("Visiting %s %s\n", typeOfTraversal, node.getNodeClass().shortName()));
            return (RuntimeType) matchingMethod.invoke(visitor, node);
        } catch (Exception e){
            errorMessages.add(String.format("Encountered %s during %s execution.\n", e, node.getNodeClass().shortName()));
            e.printStackTrace();
        }
        return null;
    }
    //////////////////////////// Control Flow Visit Methods ///////////////////////////////////////////

    public class ControlFlowVisit implements NodalVisitor {
        public RuntimeType visit(StartNode node){
            execute(this, node.next());
            return null;
        }

        public RuntimeType visit(BeginNode node){
            execute(this, node.next()); // todo deal with frame states etc.
            return null;
        }

        public RuntimeType visit(EndNode node){
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
            // Evaluates the phis associated with the given merge node.
            applyMerge(node);
            // Continue to following cfg node
            execute(this, node.next());
            return null;
        }

        public RuntimeType visit(LoopBeginNode node) {
            // Evaluates the phis associated with the given merge node.
            applyMerge(node);
            // Continue to following cfg node
            execute(this, node.next());
            return null;
        }

        public RuntimeType visit(LoopExitNode node) {
            execute(this, node.next());
            return null;
        }

        public RuntimeType visit(ValueProxyNode node) {
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
            activationStack.push(new ActivationRecord(node));
            log(String.format("The arguments supplied to the invoke are %s", activationStack.peek()));

            CallTargetNode callNode = node.callTarget();
            StructuredGraph methodGraph = create_subgraph(callNode);
            //RuntimeType methodOut = execute(this, methodGraph.start());
            RuntimeType methodOut = interpreter.executeGraph(methodGraph);

            state.put(node, methodOut);  // Used when visiting Invoke node as Data
            log(String.format("The returned value from the function call was: %s", methodOut.toString()) );

            execute(this, node.next());
            return null;
        }

        public RuntimeType visit(IntegerEqualsNode node) {
            return null;
        }

        // todo - de-optimisation on graphs?
        public RuntimeType visit(FixedGuardNode node) {
            execute(this, node.next());
            return null;
        }

        public RuntimeType visit(NewArrayNode node) {
            RuntimeType length = execute(new DataFlowVisit(), node.length());
            // todo handle deletion of state entries (That is, implement a garbage collector for the 'heap')
            state.put(node, new RTArray(length, node.elementType())); // Creates array on 'heap'

            execute(this, node.next());
            return null;
        }

        @Override
        public RuntimeType visit(ArrayLengthNode node) {

            execute(this, node.next());
            return null;
        }

        @Override
        public RuntimeType visit(StoreIndexedNode node) {
            RTArray array = (RTArray) execute(new DataFlowVisit(), node.array());
            RuntimeType index = execute(new DataFlowVisit(), node.index());
            RuntimeType value = execute(new DataFlowVisit(), node.value());

            assert array != null;
            array.set_index(index, value);
            execute(this, node.next());
            return null;
        }

        @Override
        public RuntimeType visit(LoadIndexedNode node) {
            execute(this, node.next());
            return null;
        }

        public RuntimeType visit(StoreFieldNode node){
            log(String.format("Storing value: %s in %s - specifically in offset  %s\n", node.value(), node.field(), node.field().getOffset()));

            RuntimeType value = execute(new DataFlowVisit(), node.value());
            offsetMapping.put(node.field().getOffset(), value);

            execute(this, node.next());
            return null;
        }

        public RuntimeType visit(LoadFieldNode node){
            // Execute LoadFieldNode eagerly as control flow edges are traversed
            // Assign value from offset to node. - may be null
            int offset = node.field().getOffset();

            RuntimeType valueAtOffset = offsetMapping.get(offset);
            state.put(node, valueAtOffset);

            execute(this, node.next());
            return null;
        }

        public RuntimeType visit(AddNode node) {
            return null;
        }

        public RuntimeType visit(SubNode node) {
            return null;
        }

        public RuntimeType visit(MulNode node) {
            return null;
        }

        public RuntimeType visit(RightShiftNode node) {
            return null;
        }

        public RuntimeType visit(LeftShiftNode node) {
            return null;
        }

        public RuntimeType visit(UnsignedRightShiftNode node) {
            return null;
        }

        public RuntimeType visit(SignedDivNode node) {
            return execute(this, node.next());
        }

        public RuntimeType visit(IfNode node) {
            RuntimeType condition = execute(new DataFlowVisit(), node.condition());

            assert condition != null;
            if (condition.getBoolean()){
                log("The condition evaluated to True");
                execute(this, node.trueSuccessor());
            } else {
                log("The condition evaluated to False");
                execute(this, node.falseSuccessor());
            }

            return null;
        }

        public RuntimeType visit(IntegerLessThanNode node) {
            return null;
        }

        public RuntimeType visit(ParameterNode node) {
            return null;
        }

        public RuntimeType visit(ReturnNode node) {
            RuntimeType out;
            if (node.result() != null){ // May have return node with no associated result ValueNode
                out = execute(new DataFlowVisit(), node.result());
            } else {
                out = new RTVoid();
            }
            activationStack.peek().set_return(out);
            return null;
        }

        public RuntimeType visit(ConstantNode node) {
            return null;
        }

        public RuntimeType visit(ValuePhiNode node) {
            return null;
        }
    }

    //////////////////////////// Data Flow Visit Methods ///////////////////////////////////////////

    public class DataFlowVisit implements NodalVisitor {
        public RuntimeType visit(StartNode node) {
            return null;
        }

        public RuntimeType visit(BeginNode node) {
            return null;
        }

        public RuntimeType visit(EndNode node) {
            return null;
        }

        public RuntimeType visit(StoreFieldNode node) {
            return null;
        }

        public RuntimeType visit(LoadFieldNode node){
            RuntimeType value = state.get(node);

            if (value != null){
                return value;
            } else {
                log(String.format("No data stored in field - returning GARBAGE from %s\n\n", node.field().getOffset()));
                errorMessages.add( String.format("Load from field without stored value (ID: %s, Field Offset: %s)", node.id(), node.field().getOffset()));
                return null;
            }
        }

        //Uses reflection to access getter methods.
        private RuntimeType general_binary_helper(ValueNode node, BiFunction<RTInteger, RTInteger, RuntimeType> operator) {
            RuntimeType x_value;
            RuntimeType y_value;
            try {
                Method getX = node.getClass().getMethod("getX");
                Method getY = node.getClass().getMethod("getY");
                x_value = execute(this, (ValueNode) getX.invoke(node));
                y_value = execute(this, (ValueNode) getY.invoke(node));
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

        public RuntimeType visit(SubNode node){
            return general_binary_helper(node, RTInteger::sub);
        }

        public RuntimeType visit(MulNode node) {
            return general_binary_helper(node, RTInteger::mul);
        }

        public RuntimeType visit(RightShiftNode node) {
            return general_binary_helper(node, RTInteger::rightShift);
        }

        public RuntimeType visit(LeftShiftNode node) {
            return general_binary_helper(node, RTInteger::leftShift);
        }

        public RuntimeType visit(UnsignedRightShiftNode node) {
            return general_binary_helper(node, RTInteger::unsignedRightShift);
        }

        public RuntimeType visit(AddNode node) {
            return general_binary_helper(node, RTInteger::add);
        }

        public RuntimeType visit(IntegerLessThanNode node) {
            return general_binary_helper(node, RTInteger::lessThan);
        }

        public RuntimeType visit(IntegerEqualsNode node) {
            return general_binary_helper(node, RTInteger::integerEquals);
        }

        // todo making the assumption this is always with ints.
        public RuntimeType visit(SignedDivNode node) {
            return general_binary_helper(node, RTInteger::signedDiv);
        }

        public RuntimeType visit(FixedGuardNode node) { //todo
            return null;
        }

        public RuntimeType visit(NewArrayNode node) {
            return state.get(node);
        }

        @Override
        public RuntimeType visit(ArrayLengthNode node) {
            RTArray array = (RTArray) execute(this, node.array());
            assert array != null;
            return array.getLength();
        }

        @Override
        public RuntimeType visit(StoreIndexedNode node) {
            return null;
        }

        @Override
        public RuntimeType visit(LoadIndexedNode node) {
            RTArray array = (RTArray) execute(this, node.array());
            RuntimeType index = execute(this, node.index());
            assert array != null;
            return array.get(index);
        }

        public RuntimeType visit(IfNode node) {
            return null;
        }

        public RuntimeType visit(ParameterNode node) {
            ArrayList<RuntimeType> parameters = activationStack.peek().getEvaluatedParameters();
            try {
                return parameters.get(node.index()); // similar to Phi node - pre-evaluated from InvokeNode
            } catch (IndexOutOfBoundsException e){
                errorMessages.add( String.format("Invalid parameter index %s in %s", node.index(), node) );
                return null;
            }
        }

        public RuntimeType visit(LoopBeginNode node) {
            return null;
        }

        public RuntimeType visit(LoopEndNode node) {
            return null;
        }

        public RuntimeType visit(LoopExitNode node) {
            return null;
        }

        public RuntimeType visit(ValueProxyNode node) {
            return execute(this, node.getOriginalNode());
        }

        public RuntimeType visit(InvokeNode node) {
            return state.get(node); // todo assumes the invoke node has previously been visited in control flow.
        }

        public RuntimeType visit(ReturnNode node) {
            return null;
        }

        public RuntimeType visit(ConstantNode node){
            return new RTInteger(((JavaConstant) node.getValue()).asInt());
        }

        public RuntimeType visit(MergeNode node) {
            return null;
        }

        public RuntimeType visit(ValuePhiNode node) {
            return state.get(node);
        }
    }
}