package org.graalvm.compiler.core;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.core.runtimetypes.RTArray;
import org.graalvm.compiler.core.runtimetypes.RTBoolean;
import org.graalvm.compiler.core.runtimetypes.RTException;
import org.graalvm.compiler.core.runtimetypes.RTFactory;
import org.graalvm.compiler.core.runtimetypes.RTInstance;
import org.graalvm.compiler.core.runtimetypes.RTNumber;
import org.graalvm.compiler.core.runtimetypes.RTVoid;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.KillingBeginNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodalVisitor;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.RuntimeType;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.ValueProxyNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.FloatEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.SignedDivNode;
import org.graalvm.compiler.nodes.calc.SignedRemNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.UnsignedRightShiftNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.debug.BlackholeNode;
import org.graalvm.compiler.nodes.debug.ControlFlowAnchorNode;
import org.graalvm.compiler.nodes.extended.AbstractBoxingNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.GetClassNode;
import org.graalvm.compiler.nodes.extended.OpaqueNode;
import org.graalvm.compiler.nodes.extended.StateSplitProxyNode;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.FinalFieldBarrierNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.RegisterFinalizerNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.phases.tiers.HighTierContext;

import java.lang.reflect.Field;
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
    private Exception exception = null;

    private final Map<Node, Integer> mergeIndexMapping = new HashMap<>(); // for Merge Nodes to
                                                                          // 'remember' which Phi to
                                                                          // eval
    private final ActivationStack activationStack = new ActivationStack(); // invokes push, return
                                                                           // (from execution) pops
    private final Map<Node, RuntimeType> heap = new HashMap<>(); // Used for 'new' calls e.g. Arrays
                                                                 // and class Instances
    private final Map<ResolvedJavaField, RuntimeType> fieldMap = new HashMap<>();

    private volatile Node nextControlNode; // Next node to execute

    // todo double check - currently stores *both* static and instance fields
    // (as we are usually just given the resolved field as a reference)

    public GraalInterpreter(HighTierContext context, boolean shouldLog) {
        this.activationStack.push(new ActivationRecord());
        this.logging = shouldLog;
        this.context = context;
    }

    private void log(String input) {
        if (logging) {
            String spaces = new String(new char[indent * 4]).replace("\0", " ");
            System.err.println(spaces + input);
        }
    }

    public void setLogging(boolean shouldLog) {
        logging = shouldLog;
    }

    // If the final return value of the function is null then execution likely halted prematurely
    // (should be RT type)
    // Or one of the DataVisits is not fully implemented
    public RuntimeType executeGraph(StructuredGraph graph, Object... args) { // todo throw
                                                                             // exception?
        indent = indent + 1;

        // todo remove
        log(String.format("Generated graph is : %s", graph.toString()));
        for (Node graphEntry : graph.getNodes()) {
            log(graphEntry.toString());
        }
        ////////////////////////

        // Store evaluated runTime values (in order) in current activation Frame)
        if (args.length != 0) {
            ActivationRecord currentFrame = activationStack.peek();
            ArrayList<RuntimeType> evaluatedParameters = currentFrame.getEvaluatedParameters();
            for (Object arg : args) {
                log(String.format("Evaluating %s param", arg));
                RuntimeType rtArg = RTFactory.toRuntimeType(arg, context);
                evaluatedParameters.add(rtArg); // add to evaluated Parameters array
            }
        }

        // Create / Store any class static fields as needed: todo
        loadStaticFields(graph);

        log(String.format("----Now executing graph: %s------\n", graph.asJavaMethod()));
        ControlFlowVisit controlVisit = new ControlFlowVisit();

        nextControlNode = graph.start(); // Sets the "start node" as the next node to execute

        while (nextControlNode != null) {
            // visit calls on control nodes set the next control node to be executed.
            execute(controlVisit, nextControlNode);
        }

        if (!errorMessages.isEmpty()) {
            log("Encountered the following errors: ");
            for (String err : errorMessages) {
                log(err);
            }
            if (exception != null) {
                // exception.printStackTrace();
                return new RTException(exception);
            }
            return new RTVoid(); // do we want to return RTVoid or Null?->RTVoid allows .toObject on
                                 // result of executeGraph
        }
        log(String.format("The return value was: %s\n", activationStack.peek().get_return()));
        // log(String.format("Current state is: \n%s\n Current Stack is: \n%s\n", heap, activationStack));
        log(String.format("-----End graph execution: %s-------\n", graph.asJavaMethod()));
        indent = indent - 1;
        return activationStack.pop().get_return();
    }

    private void loadStaticFields(StructuredGraph graph) {
        // Get class of graph's root method. e.g. rootClass.getJavaMirror();
        JavaType hotSpotClassObjectType = graph.asJavaMethod().getDeclaringClass();
        Object hotSpotClassObjectConstant = null;
        try {
            Field classMirror = hotSpotClassObjectType.getClass().getDeclaredField("mirror");
            classMirror.setAccessible(true);
            hotSpotClassObjectConstant = classMirror.get(hotSpotClassObjectType);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        Class<?> actualDeclaringClass = null;
        try {
            Field objectField = hotSpotClassObjectConstant.getClass().getDeclaredField("object");
            objectField.setAccessible(true);
            actualDeclaringClass = (Class<?>) objectField.get(hotSpotClassObjectConstant);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

        // Get all fields using reflection. getDeclaredFields for any access modifier, getFields for
        // inherited fields.
        // todo use .getFields()
        assert actualDeclaringClass != null;
        Field[] fields = actualDeclaringClass.getDeclaredFields();

        // Store these fields with their resolvedField types in the field map.
        for (Field currentField : fields) {
            try {
                currentField.setAccessible(true);
                ResolvedJavaField resolvedField = context.getMetaAccess().lookupJavaField(currentField);
                if (resolvedField.isStatic()) { // todo Could this work for instance fields?
                    fieldMap.put(resolvedField, RTFactory.toRuntimeType(currentField.get(null)));
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    // Helper methods for dealing with data visits on nodes
    // Adds a 'reference' to data object in current (top) activation record
    private void addVariable(Node node, RuntimeType value) {
        activationStack.peek().addLocalVar(node, value);
    }

    private RuntimeType getVariable(Node node) {
        return activationStack.peek().getLocalVar(node);
    }

    private class ActivationStack {
        private final ArrayList<ActivationRecord> stack = new ArrayList<>();

        public ActivationRecord pop() {
            return stack.remove(0);
        }

        public void push(ActivationRecord activationRecord) {
            activationRecord.set_depth(stack.size());
            stack.add(0, activationRecord);
        }

        public ActivationRecord peek() {
            return stack.get(0);
        }

        public String toString() {
            return "ActivationStack{" + "stack=" + stack + '}';
        }
    }

    private class ActivationRecord {
        private final ArrayList<RuntimeType> evaluatedParameters;
        private final ArrayList<ValueNode> originalArguments;
        private final Map<Node, RuntimeType> localState;
        // private final InvokeNode activationNode;
        private RuntimeType returnValue = new RTVoid(); // functions are assumed to return null
                                                        // unless otherwise set.
        private int depth;

        // Creates an activation record from an InvokeNode / InvokeWithExceptionNode using their
        // CallTargetNode
        public ActivationRecord(CallTargetNode callNode) {
            localState = new HashMap<>();
            evaluatedParameters = new ArrayList<>();
            originalArguments = new ArrayList<>(Arrays.asList(callNode.arguments().toArray(new ValueNode[0])));

            log(String.format("Supplied (unevaluated) args to invoke node are %s", callNode.arguments()));

            // Evaluates each of the given parameters
            for (ValueNode arg : originalArguments) {
                RuntimeType argVal = execute(new DataFlowVisit(), arg);
                evaluatedParameters.add(argVal);
            }

        }

        // Creates empty activation record
        public ActivationRecord() {
            evaluatedParameters = new ArrayList<>();
            originalArguments = new ArrayList<>();
            localState = new HashMap<>();
        }

        public void addLocalVar(Node node, RuntimeType value) {
            localState.put(node, value);
        }

        public RuntimeType getLocalVar(Node node) {
            return localState.get(node);
        }

        public void set_depth(int depth) {
            this.depth = depth;
        }

        public void set_return(RuntimeType output) {
            returnValue = output;
        }

        public RuntimeType get_return() {
            return returnValue;
        }

        public ArrayList<RuntimeType> getEvaluatedParameters() {
            return evaluatedParameters;
        }

        public String toString() {
            return String.format("\n*Activation Record @ Depth %s:Parameters:\n%s\nVariables:%s*", depth, evaluatedParameters, localState);
        }
    }

    private RuntimeType execute(NodalVisitor visitor, Node node) {
        // todo cache classType to Method results in mapping
        // todo could, rather than having control nodes add nextControlNode = node.next(), set here,
        // if control visit
        // todo Potential point construct execution list.
        // Using generic 'unbounded wildcard' as the type of the node can be any subclass of Object
        Class<?>[] args = new Class<?>[1];
        args[0] = node.getClass();
        Class<?> superclass = node.getClass().getSuperclass();
        Method matchingMethod = null;
        String typeOfTraversal = "CONTROL";

        while (!superclass.equals(Object.class)) { // go up class hierarchy until matching method is
                                                   // found.
            // Determine visitor type, then get corresponding method
            try {
                if (visitor instanceof ControlFlowVisit) {
                    matchingMethod = ControlFlowVisit.class.getMethod("visit", args);
                } else {
                    matchingMethod = DataFlowVisit.class.getMethod("visit", args);
                    typeOfTraversal = "DATA";
                }
                break;
            } catch (NoSuchMethodException e) {
                // e.printStackTrace();
                args[0] = superclass;
                superclass = superclass.getSuperclass();
                if (superclass.equals(Object.class)) {
                    errorMessages.add(String.format("UNIMPLEMENTED CASE: Encountered %s %s %s\n", typeOfTraversal, node.getNodeClass().shortName(), node.getClass()));
                    // todo streamline -- halts execution
                    nextControlNode = null;
                    return null;
                }
            }
        }

        try {
            log(String.format("Visiting %s (%s) %s\n", typeOfTraversal, node.id(), node.getNodeClass().shortName()));
            assert matchingMethod != null;
            return (RuntimeType) matchingMethod.invoke(visitor, node);
        } catch (InvocationTargetException e) {
            errorMessages.add(String.format("Encountered %s during %s execution.\n", e.getTargetException(), node.getNodeClass().shortName()));
            nextControlNode = null;
            // e.printStackTrace();
            e.getTargetException().printStackTrace();
            exception = e;
            // exception = (Exception) e.getTargetException();
        } catch (IllegalAccessException e) { // todo shouldn't happen
            errorMessages.add(String.format("Encountered %s during %s execution.\n", e, node.getNodeClass().shortName()));
            nextControlNode = null;
            e.printStackTrace();
        }
        return null;
    }

    //////////////////////////// Control Flow Visit Methods
    //////////////////////////// ///////////////////////////////////////////

    public class ControlFlowVisit implements NodalVisitor {
        public RuntimeType visit(StartNode node) {
            nextControlNode = node.next();
            // todo could alternatively return the next node to execute? return this.next() ?
            // this.next().executeControl(GraalInterpreter)
            // node.execute(GraalInterpreter)
            return null;
        }

        public RuntimeType visit(BeginNode node) {
            nextControlNode = node.next();
            return null;
        }

        public RuntimeType visit(EndNode node) {
            for (Node nextNode : node.cfgSuccessors()) { // Should just have one successor
                AbstractMergeNode mergeNode = (AbstractMergeNode) nextNode;
                int index = mergeNode.phiPredecessorIndex(node);
                log("Mapping " + mergeNode + " to index " + index + " (" + node + ")");
                mergeIndexMapping.put(mergeNode, index);
                nextControlNode = mergeNode;
            }
            return null;
        }

        public RuntimeType visit(LoopEndNode node) {
            LoopBeginNode loopBeginNode = node.loopBegin();
            int phiIndex = loopBeginNode.phiPredecessorIndex(node);
            log("The phi index for the loopBegin node following: " + node + " is " + phiIndex);
            mergeIndexMapping.put(loopBeginNode, phiIndex);

            nextControlNode = loopBeginNode; // Note, not node.next()
            return null;
        }

        private void applyMerge(AbstractMergeNode node) {
            // Gets the index from which this merge node was reached.
            int accessIndex = mergeIndexMapping.get(node);
            // Get all associated phi nodes of this merge node
            // Evaluate all associated phi nodes with merge access index as their input
            log("Evaluating phis for " + node + " at index " + accessIndex);

            // store all the INITIAL values (before updating) in local mapping then apply from that
            // to avoid mapping new state when the prev state should have been used
            // e.g. a phi node with data input from a phi node.
            Map<Node, RuntimeType> prevValues = new HashMap<>();
            // Collecting previous values:
            for (PhiNode phi : node.phis()) {
                log("---- Grabbing old value from " + phi + " ------");
                RuntimeType prevVal = execute(new DataFlowVisit(), phi);
                log("---- Old val was " + prevVal + " Evaluation ------");
                if (prevVal != null) { // Only maps if the evaluation yielded a value
                    // (i.e. not the first time the phi has been evaluated)
                    prevValues.put(phi, prevVal);
                }
            }

            for (PhiNode phi : node.phis()) {
                log("---- Start " + phi + " Evaluation ------");
                ValueNode val = phi.valueAt(accessIndex);
                RuntimeType phiVal;
                if (prevValues.containsKey(val)) {
                    phiVal = prevValues.get(val);
                } else {
                    phiVal = execute(new DataFlowVisit(), val);
                }

                log("---- End " + phi + " Evaluation ------");
                addVariable(phi, phiVal); // phi val is accessed in data execute
            }
        }

        public RuntimeType visit(MergeNode node) {
            // Evaluates the phis associated with the given merge node.
            applyMerge(node);
            // Continue to following cfg node
            nextControlNode = node.next();
            return null;
        }

        public RuntimeType visit(LoopBeginNode node) {
            // Evaluates the phis associated with the given merge node.
            applyMerge(node);
            // Continue to following cfg node
            nextControlNode = node.next();
            return null;
        }

        public RuntimeType visit(LoopExitNode node) {
            nextControlNode = node.next();
            return null;
        }

        public RuntimeType visit(ValueProxyNode node) {
            return null;
        }

        private StructuredGraph create_subgraph(CallTargetNode callNode) {
            // Construct a graph for method associated with call node. todo store prev. constructed
            // methods?
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
            activationStack.push(new ActivationRecord(node.callTarget()));
            log(String.format("Invoke arguments:%s", activationStack.peek().getEvaluatedParameters()));

            CallTargetNode callNode = node.callTarget();
            StructuredGraph methodGraph = create_subgraph(callNode);

            // log(String.format("Generated graph is : %s", methodGraph.toString()));
            // for(Node graphEntry : methodGraph.getNodes()){log(graphEntry.toString());}

            RuntimeType methodOut = null;
            methodOut = interpreter.executeGraph(methodGraph); // todo removed catch for invoke
                                                               // exception

            addVariable(node, methodOut); // Used when visiting Invoke node as Data
            log(String.format("The returned value from the function call was: %s", methodOut));
            nextControlNode = node.next();
            return null;
        }

        public RuntimeType visit(IntegerEqualsNode node) {
            return null;
        }

        public RuntimeType visit(FloatEqualsNode node) {
            return null;
        }

        @Override
        public RuntimeType visit(StateSplitProxyNode node) {
            RuntimeType value = execute(new DataFlowVisit(), node.getOriginalNode());
            addVariable(node.getOriginalNode(), value);
            nextControlNode = node.next();
            return null;
        }

        // todo - de-optimisation on graphs?
        public RuntimeType visit(FixedGuardNode node) {
            nextControlNode = node.next();
            return null;
        }

        public RuntimeType visit(NewArrayNode node) {
            RuntimeType length = execute(new DataFlowVisit(), node.length());
            // todo handle deletion of state entries (That is, implement a garbage collector for the
            // 'heap')
            // todo utilise stack frame to handle memory management when no reference to object
            // exists?
            heap.put(node, new RTArray(length, node.elementType())); // Creates array on 'heap'

            nextControlNode = node.next();
            return null;
        }

        @Override
        public RuntimeType visit(ArrayLengthNode node) {
            nextControlNode = node.next();
            return null;
        }

        @Override
        public RuntimeType visit(StoreIndexedNode node) {
            RTArray array = (RTArray) execute(new DataFlowVisit(), node.array());
            RuntimeType index = execute(new DataFlowVisit(), node.index());
            RuntimeType value = execute(new DataFlowVisit(), node.value());

            assert array != null;
            array.set_index(index, value);
            nextControlNode = node.next();
            return null;
        }

        @Override
        public RuntimeType visit(LoadIndexedNode node) {
            // todo consider also eagerly evaluating array?
            RuntimeType index = execute(new DataFlowVisit(), node.index()); // this may be a phi
                                                                            // node, eagerly
                                                                            // evaluate
            addVariable(node, index);
            nextControlNode = node.next();
            return null;
        }

        public RuntimeType visit(StoreFieldNode node) {
            // //todo Use local variable search on activation stack?
            RuntimeType value = execute(new DataFlowVisit(), node.value());

            if (node.isStatic()) {
                // Global map from field to RuntimeValue
                fieldMap.put(node.field(), value);
            } else {
                // Find associated instance and map store runtime val in instance field
                ValueNode object = node.object();
                RTInstance matchingInstance = (RTInstance) execute(new DataFlowVisit(), object);
                log(String.format("The corresponding object to the store field is: %s", matchingInstance));
                assert matchingInstance != null;
                matchingInstance.setFieldValue(node.field(), value);
            }

            nextControlNode = node.next();
            return null;
        }

        public RuntimeType visit(LoadFieldNode node) {
            RuntimeType value;

            if (node.isStatic()) {
                log("-- Static field access");
                value = fieldMap.get(node.field());
            } else {
                // Find associated instance and map load runtime val from instance field
                RTInstance matchingInstance = (RTInstance) execute(new DataFlowVisit(), node.object());
                assert matchingInstance != null;
                value = matchingInstance.getFieldValue(node.field());
                log(String.format("-- Instance field access of %s", matchingInstance));
            }

            addVariable(node, value); // todo unused in loadField data visit
            nextControlNode = node.next();
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
            nextControlNode = node.next();
            return null;
        }

        @Override
        public RuntimeType visit(SignedRemNode node) {
            nextControlNode = node.next();
            return null;
        }

        public RuntimeType visit(NewInstanceNode node) {
            // todo potentially employ stack frame for GC?
            heap.put(node, new RTInstance(node.instanceClass()));
            nextControlNode = node.next();
            return null;
        }

        // todo avoid using this: generalise away the need to create a graph for the object init
        // method.
        public RuntimeType visit(RegisterFinalizerNode node) {
            nextControlNode = node.next();
            return null;
        }

        public RuntimeType visit(FinalFieldBarrierNode node) {
            nextControlNode = node.next();
            return null;
        }

        public RuntimeType visit(PiNode node) {
            return null;
        }

        // todo assume we unbox primitives - currently only tested for Integer unboxing
        public RuntimeType visit(UnboxNode node) {
            ResolvedJavaField unboxField = ((FieldLocationIdentity) node.getLocationIdentity()).getField();
            RuntimeType value; // the 'unboxed' (primitive) value

            if (unboxField.isStatic()) {
                value = fieldMap.getOrDefault(unboxField, new RTVoid());
            } else {
                RTInstance matchingInstance = (RTInstance) execute(new DataFlowVisit(), node.getValue());
                assert matchingInstance != null;
                value = matchingInstance.getFieldValue(unboxField);
            }

            // replace value with correct primitive: todo look at JavaKind for other helper methods
            RuntimeType unboxedValue = value;
            // https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.2-200
            switch (node.getBoxingKind()) {
                case Boolean:
                    unboxedValue = ((RTNumber) value).createRuntimeBoolean();
                    break;
                case Void:
                    unboxedValue = new RTVoid();
                    break;
                // todo add more cases (for all primitives)
                default:
                    break;
            }

            addVariable(node, unboxedValue);

            nextControlNode = node.next();
            return null;
        }

        public RuntimeType visit(AbstractBoxingNode node) {
            RuntimeType value = execute(new DataFlowVisit(), node.getValue());
            addVariable(node, value);
            nextControlNode = node.next();
            return null;
        }

        public RuntimeType visit(ZeroExtendNode node) {
            return null;
        }

        public RuntimeType visit(NarrowNode node) {
            return null;
        }

        public RuntimeType visit(ObjectEqualsNode node) {
            return null;
        }

        public RuntimeType visit(GetClassNode node) {
            return null;
        }

        @Override
        public RuntimeType visit(BlackholeNode node) {
            // Used in BlackholeDirectiveTest
            nextControlNode = node.next();
            return null;
        }

        @Override
        public RuntimeType visit(ControlFlowAnchorNode node) {
            // ControlFlowAnchorDirectiveTest
            nextControlNode = node.next();
            return null;
        }

        @Override
        public RuntimeType visit(BranchProbabilityNode node) {
            // todo implement
            return null;
        }

        @Override
        public RuntimeType visit(DeoptimizeNode node) {
            // todo potentially generate graph for method?
            nextControlNode = null;// potentially set next control node to start of method?
            return null;
        }

        @Override
        public RuntimeType visit(ConditionalNode node) {
            return null; // Similar to if node, but only used for direct evaluation (e.g. DataFlow
                         // evaluation)
        }

        @Override
        public RuntimeType visit(InvokeWithExceptionNode node) {
            // todo Treating the same as an invoke node, though may possibly cause an exception
            // (in which case, set the next node to return?)
            activationStack.push(new ActivationRecord(node.callTarget()));
            log(String.format("Invoke arguments:%s", activationStack.peek().getEvaluatedParameters()));

            CallTargetNode callNode = node.callTarget();
            StructuredGraph methodGraph = create_subgraph(callNode);

            RuntimeType methodOut = null;
            methodOut = interpreter.executeGraph(methodGraph);

            addVariable(node, methodOut); // Used when visiting Invoke node as Data
            log(String.format("The returned value from the function call was: %s", methodOut));

            if (methodOut instanceof RTException) {
                nextControlNode = node.exceptionEdge();
            } else {
                nextControlNode = node.next();
            }

            return null;
        }

        @Override
        public RuntimeType visit(OpaqueNode node) {
            return null;
        }

        @Override
        public RuntimeType visit(IsNullNode node) {
            return null;
        }

        @Override
        public RuntimeType visit(KillingBeginNode node) {
            // todo should "kill" a single memory location - getKilledLocationIdentity
            nextControlNode = node.next();
            return null;
        }

        @Override
        public RuntimeType visit(SignExtendNode node) { // FloatingNode
            return null;
        }

        public RuntimeType visit(IfNode node) {
            RuntimeType condition = execute(new DataFlowVisit(), node.condition());

            assert condition != null;
            if (condition.getBoolean()) {
                log("The condition evaluated to True");
                nextControlNode = node.trueSuccessor();
            } else {
                log("The condition evaluated to False");
                nextControlNode = node.falseSuccessor();
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
            if (node.result() != null) { // May have return node with no associated result ValueNode
                out = execute(new DataFlowVisit(), node.result());
            } else {
                out = new RTVoid();
            }
            activationStack.peek().set_return(out);
            nextControlNode = null; // this should be the last node in the execution
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

        public RuntimeType visit(LoadFieldNode node) {
            RuntimeType value;

            if (node.isStatic()) {
                value = fieldMap.get(node.field());
            } else {
                // todo is it possible that the reference to the object does not exist in the
                // current stack frame,
                // but only in the fieldMap?
                value = getVariable(node);
                if (value == null) {
                    log(String.format("getVariable on instance not in stack frame: %s\n", node));
                    RTInstance matchingInstance = (RTInstance) execute(new DataFlowVisit(), node.object());
                    assert matchingInstance != null;
                    value = matchingInstance.getFieldValue(node.field());
                }
            }

            if (value != null) {
                return value;
            } else {
                errorMessages.add(String.format("Load from field without stored value (%s)", node.field().format("(%f) %t %n:")));
                return null; // todo return RTVoid?
            }
        }

        // Uses reflection to access getter methods.
        private RuntimeType general_binary_helper(ValueNode node, BiFunction<RTNumber, RTNumber, RuntimeType> operator) {
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
            if (!(x_value instanceof RTNumber) || !(y_value instanceof RTNumber)) {
                errorMessages.add(String.format("Invalid inputs to (%s): %s %s", node, x_value, y_value));
            }
            assert x_value instanceof RTNumber;
            assert y_value instanceof RTNumber;
            RTNumber x_integer = (RTNumber) x_value;
            RTNumber y_integer = (RTNumber) y_value;
            return operator.apply(x_integer, y_integer);
        }

        public RuntimeType visit(SubNode node) {
            return general_binary_helper(node, RTNumber::sub);
        }

        public RuntimeType visit(MulNode node) {
            return general_binary_helper(node, RTNumber::mul);
        }

        public RuntimeType visit(RightShiftNode node) {
            return general_binary_helper(node, RTNumber::rightShift);
        }

        public RuntimeType visit(LeftShiftNode node) {
            return general_binary_helper(node, RTNumber::leftShift);
        }

        public RuntimeType visit(UnsignedRightShiftNode node) {
            return general_binary_helper(node, RTNumber::unsignedRightShift);
        }

        public RuntimeType visit(AddNode node) {
            return general_binary_helper(node, RTNumber::add);
        }

        public RuntimeType visit(IntegerLessThanNode node) {
            return general_binary_helper(node, RTNumber::lessThan);
        }

        public RuntimeType visit(IntegerEqualsNode node) {
            return general_binary_helper(node, RTNumber::numberEquals);
        }

        public RuntimeType visit(FloatEqualsNode node) {
            return general_binary_helper(node, RTNumber::numberEquals);
        }

        @Override
        public RuntimeType visit(StateSplitProxyNode node) {
            return getVariable(node.object());
        }

        public RuntimeType visit(SignedDivNode node) {
            return general_binary_helper(node, RTNumber::signedDiv);
        }

        @Override
        public RuntimeType visit(SignedRemNode node) {
            return general_binary_helper(node, RTNumber::signedRem);
        }

        public RuntimeType visit(NewInstanceNode node) {
            return heap.get(node);
        }

        public RuntimeType visit(RegisterFinalizerNode node) {
            return null;
        }

        public RuntimeType visit(FinalFieldBarrierNode node) {
            return null;
        }

        public RuntimeType visit(PiNode node) {
            return execute(this, node.getOriginalNode());
        }

        public RuntimeType visit(UnboxNode node) {
            // Note, does not attempt to use / construct java.lang object but rather uses
            // RTInstance.
            return getVariable(node);
        }

        public RuntimeType visit(AbstractBoxingNode node) {
            return getVariable(node);
        }

        public RuntimeType visit(NarrowNode node) {
            return execute(this, node.getValue());
        }

        public RuntimeType visit(ObjectEqualsNode node) {
            // Compare fields? / Share same memory address? todo currently only for Arrays Objects
            // (not class objects)
            RuntimeType x = execute(this, node.getX());
            RuntimeType y = execute(this, node.getY());
            // Two null objects are equal
            if (x == null && y == null) {
                return new RTBoolean(true);
            }

            if (x instanceof RTVoid && y instanceof RTVoid) {
                return new RTBoolean(true);
            }
            // Two arrays are considered equal if : They have the same number of elements, all pairs
            // of elems are equal.
            assert x != null;
            assert y != null;

            if (x.getClass().equals(y.getClass())) {
                // x.equals(y);
                // todo move logic to ArrayNode
                if (x instanceof RTArray && y instanceof RTArray) {
                    RTArray x_arr = (RTArray) x;
                    RTArray y_arr = (RTArray) y;
                    if (x_arr.getResolvedLength() == y_arr.getResolvedLength()) {
                        for (int i = 0; i < x_arr.getResolvedLength(); i++) {
                            RTNumber index = new RTNumber(i);
                            RuntimeType x_entry = x_arr.get(index);
                            RuntimeType y_entry = y_arr.get(index);
                            if (!x_entry.toObject().equals(y_entry.toObject())) {
                                return new RTBoolean(false);
                            }
                        }
                        return new RTBoolean(true);
                    }
                }
            }
            return new RTBoolean(false);
        }

        public RuntimeType visit(GetClassNode node) {
            return null;
        }

        @Override
        public RuntimeType visit(BlackholeNode node) {
            return null;
        }

        @Override
        public RuntimeType visit(ControlFlowAnchorNode node) {
            return null;
        }

        @Override // todo check
        public RuntimeType visit(BranchProbabilityNode node) {
            // return null;
            RuntimeType prob = execute(this, node.getProbability());
            RuntimeType cond = execute(this, node.getCondition());

            log(String.format("Prob: %s, Cond: %s", prob, cond));

            assert cond != null;
            addVariable(node.getCondition(), new RTBoolean(cond.getBoolean()));

            return cond;

            // return prob; //todo currently ignoring prob...
        }

        @Override
        public RuntimeType visit(DeoptimizeNode node) {
            return null;
        }

        @Override
        public RuntimeType visit(ConditionalNode node) {
            RuntimeType condition = execute(this, node.condition());
            assert condition != null;
            RuntimeType conditionalValue;

            if (condition.getBoolean()) {
                log("The conditional Node evaluated to True");
                conditionalValue = execute(this, node.trueValue()); // todo consider non boolean
                                                                    // vals?
            } else {
                log("The conditional Node evaluated to False");
                conditionalValue = execute(this, node.falseValue());
            }
            assert conditionalValue != null;
            log(String.format("The conditional value evaluated to %s", conditionalValue));
            return conditionalValue;
        }

        @Override
        public RuntimeType visit(InvokeWithExceptionNode node) {
            return getVariable(node);
        }

        @Override
        public RuntimeType visit(OpaqueNode node) {
            return execute(this, node.getValue());

        }

        @Override
        public RuntimeType visit(IsNullNode node) {
            // An IsNullNode will be true if the supplied value is null, and false if it is
            // non-null.
            RuntimeType node_value = execute(this, node.getValue());

            return new RTBoolean(node_value instanceof RTVoid);
        }

        @Override
        public RuntimeType visit(KillingBeginNode node) {
            return null;
        }

        private void unary_number_converter(RuntimeType value, int inputBits, int resultBits, boolean isSigned) {
            // Todo naive implementation, checks the result bits of the zero extend and coerces
            // RTNumber to 'fit'
            if (inputBits < resultBits) { // todo should likely work with stamps instead
                if (value instanceof RTNumber) { // todo currently only coerces to long, otherwise
                                                 // no effect
                    ((RTNumber) value).coerceValue(resultBits, isSigned);
                }
            }
        }

        @Override
        public RuntimeType visit(SignExtendNode node) {
            // similar to ZeroExtend - converts an integer to a wider integer using sign extension.
            // e.g. 32 to 64
            // Todo assumes only Signed
            RuntimeType currentValue = execute(this, node.getValue());
            unary_number_converter(currentValue, node.getInputBits(), node.getResultBits(), true);
            return execute(this, node.getValue());
        }

        public RuntimeType visit(ZeroExtendNode node) {
            // Todo assumes only Unsigned.
            RuntimeType currentValue = execute(this, node.getValue());
            unary_number_converter(currentValue, node.getInputBits(), node.getResultBits(), false);
            return execute(this, node.getValue());
        }

        public RuntimeType visit(FixedGuardNode node) { // todo
            return null;
        }

        public RuntimeType visit(NewArrayNode node) {
            return heap.get(node);
        }

        public RuntimeType visit(ArrayLengthNode node) {
            RTArray array = (RTArray) execute(this, node.array());
            assert array != null;
            return array.getLength();
        }

        public RuntimeType visit(StoreIndexedNode node) {
            return null;
        }

        public RuntimeType visit(LoadIndexedNode node) {
            RTArray array = (RTArray) execute(this, node.array());
            RuntimeType index = getVariable(node);
            assert array != null;
            assert index != null;
            return array.get(index);
        }

        public RuntimeType visit(IfNode node) {
            return null;
        }

        public RuntimeType visit(ParameterNode node) {
            ArrayList<RuntimeType> parameters = activationStack.peek().getEvaluatedParameters();
            try {
                return parameters.get(node.index()); // similar to Phi node - pre-evaluated from
                                                     // InvokeNode
            } catch (IndexOutOfBoundsException e) {
                errorMessages.add(String.format("Invalid parameter index %s in %s", node.index(), node));
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
            return getVariable(node);
        }

        public RuntimeType visit(ReturnNode node) {
            return null;
        }

        public RuntimeType visit(ConstantNode node) {
            Constant value = node.getValue();
            if (value instanceof PrimitiveConstant) {
                return RTFactory.toRuntimeType(((PrimitiveConstant) node.getValue()).asBoxedPrimitive());
            } else { // Dealing with non primitive values
                Object hotSpotObj = null;
                try {
                    Field objectField = value.getClass().getDeclaredField("object");
                    objectField.setAccessible(true);
                    hotSpotObj = objectField.get(value);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    e.printStackTrace();
                }
                return RTFactory.toRuntimeType(hotSpotObj, context); // requires context to work
                                                                     // with hotspot JavaTypes
            }
        }

        public RuntimeType visit(MergeNode node) {
            return null;
        }

        public RuntimeType visit(ValuePhiNode node) {
            return getVariable(node);
        }
    }
}
