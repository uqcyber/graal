package org.graalvm.compiler.core;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.debug.interpreter.value.RuntimeValue;
import org.graalvm.compiler.debug.interpreter.value.RuntimeValueFactory;
import org.graalvm.compiler.debug.interpreter.value.type.RuntimeValueArray;
import org.graalvm.compiler.debug.interpreter.value.type.RuntimeValueBoolean;
import org.graalvm.compiler.debug.interpreter.value.type.RuntimeValueCharacter;
import org.graalvm.compiler.debug.interpreter.value.type.RuntimeValueNumber;
import org.graalvm.compiler.debug.interpreter.value.type.RuntimeValueString;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.util.DebugInterpreterInterface;
import org.graalvm.compiler.phases.tiers.HighTierContext;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GraalInterpreter {
    private final DebugInterpreterInterfaceImpl myState;
    private final RuntimeValueFactory valueFactory;
    private final HighTierContext context;
    private final Map<ResolvedJavaField, RuntimeValue> fieldMap = new HashMap<>();

    public GraalInterpreter(HighTierContext context) {
        this.context = context;
        this.valueFactory = new RuntimeValueFactoryImpl(context);
        this.myState = new DebugInterpreterInterfaceImpl();
    }

    public RuntimeValue executeGraph(StructuredGraph graph, Object... args) {
        ArrayList<RuntimeValue> evaluatedParams = new ArrayList<>();
        for (Object arg : args) {
            RuntimeValue argValue = valueFactory.toRuntimeType(arg, context.getMetaAccess()::lookupJavaType);
            evaluatedParams.add(argValue);
        }

        return interpretGraph(graph, evaluatedParams);
    }

    private RuntimeValue interpretGraph(StructuredGraph graph, List<RuntimeValue> evaluatedParams) {
        myState.addActivation(evaluatedParams);

        loadStaticFields(graph);

        FixedNode next = graph.start();
        RuntimeValue returnVal = null;

        while (next != null) {
            if (next instanceof ReturnNode) {
                next.interpretControlFlow(myState);
                returnVal = myState.getNodeLookupValue(next);
                break;
            }
            next = next.interpretControlFlow(myState);
        }

        // TODO: handle InvokeWithException properly

        myState.popActivation();
        return returnVal;
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
                    fieldMap.put(resolvedField, valueFactory.toRuntimeType(currentField.get(null)));
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private class DebugInterpreterInterfaceImpl implements DebugInterpreterInterface {
        // Used for 'new' calls such as arrays and object instances
        final Map<Node, RuntimeValue> heap = new HashMap<>();
        // for MergeNodes to remember which PhiNode to evaluate
        final Map<AbstractMergeNode, Integer> mergeIndexes = new HashMap<>();
        final Stack<ActivationRecord> activations = new Stack<>();

        void addActivation(List<RuntimeValue> args) {
            activations.add(new ActivationRecord(args));
        }

        ActivationRecord popActivation() {
            return activations.pop();
        }

        @Override
        public void setHeapValue(Node node, RuntimeValue value) {
            heap.put(node, value);
        }

        @Override
        public RuntimeValue getHeapValue(Node node) {
            return heap.getOrDefault(node, null);
        }

        @Override
        public void setNodeLookupValue(Node node, RuntimeValue value) {
            activations.peek().setLocalVar(node, value);
        }

        @Override
        public RuntimeValue getNodeLookupValue(Node node) {
            return activations.peek().getLocalVar(node);
        }

        @Override
        public void setMergeNodeIncomingIndex(AbstractMergeNode node, int index) {
            mergeIndexes.put(node, index);
        }

        int getMergeIndex(AbstractMergeNode node) {
            return mergeIndexes.get(node);
        }

        @Override
        public void visitMerge(AbstractMergeNode node) {
            // Gets the index from which this merge node was reached.
            int accessIndex = getMergeIndex(node);
            // Get all associated phi nodes of this merge node
            // Evaluate all associated phi nodes with merge access index as their input
            // store all the INITIAL values (before updating) in local mapping then apply from that
            // to avoid mapping new state when the prev state should have been used
            // e.g. a phi node with data input from a phi node.
            Map<Node, RuntimeValue> prevValues = new HashMap<>();

            // Collecting previous values:
            for (PhiNode phi : node.phis()) {
                RuntimeValue prevVal = this.interpretDataflowNode(node);
                if (prevVal != null) {
                    // Only maps if the evaluation yielded a value
                    // (i.e. not the first time the phi has been evaluated)
                    prevValues.put(phi, prevVal);
                }
            }

            for (PhiNode phi : node.phis()) {
                ValueNode val = phi.valueAt(accessIndex);
                RuntimeValue phiVal;
                if (prevValues.containsKey(val)) {
                    phiVal = prevValues.get(val);
                } else {
                    phiVal = this.interpretDataflowNode(val);
                }

                this.setNodeLookupValue(phi, phiVal);
            }
        }

        @Override
        public RuntimeValue interpretMethod(CallTargetNode target, List<Node> argumentNodes) {
            List<RuntimeValue> evaledArgs = argumentNodes.stream()
                    .map(this::interpretDataflowNode)
                    .collect(Collectors.toList());

            StructuredGraph methodGraph = new StructuredGraph.Builder(target.getOptions(),
                    target.getDebug(), StructuredGraph.AllowAssumptions.YES)
                    .method(target.targetMethod())
                    .build();
            context.getGraphBuilderSuite().apply(methodGraph, context);

            return interpretGraph(methodGraph, evaledArgs);
        }

        @Override
        public RuntimeValueFactory getRuntimeValueFactory() {
            return valueFactory;
        }

        @Override
        public RuntimeValue interpretDataflowNode(Node node) {
            if (!(node instanceof ValueNode)) {
                throw new RuntimeException("Tried to interpret non ValueNode as a dataflow node");
            }
            return ((ValueNode) node).interpretDataFlow(this);
        }

        @Override
        public RuntimeValue loadFieldValue(ResolvedJavaField field) {
            return fieldMap.getOrDefault(field, null);
        }

        @Override
        public void storeFieldValue(ResolvedJavaField field, RuntimeValue value) {
            fieldMap.put(field, value);
        }

        @Override
        public List<RuntimeValue> getParameters() {
            return activations.peek().evaluatedParams;
        }
    }

    private static class ActivationRecord {
        private final List<RuntimeValue> evaluatedParams;
        private final Map<Node, RuntimeValue> localState = new HashMap<>();

        ActivationRecord(List<RuntimeValue> evaluatedParams) {
            this.evaluatedParams = evaluatedParams;
        }

        void setLocalVar(Node node, RuntimeValue value) {
            localState.put(node, value);
        }

        RuntimeValue getLocalVar(Node node) {
            return localState.getOrDefault(node, null);
        }
    }

    private static class RuntimeValueFactoryImpl implements RuntimeValueFactory {

        private final HighTierContext context;

        private RuntimeValueFactoryImpl(HighTierContext context) {
            this.context = context;
        }

        @Override
        public RuntimeValue toRuntimeType(Object arg) {
            return toRuntimeType(arg, context.getMetaAccess()::lookupJavaType);
        }

        @Override
        public RuntimeValue toRuntimeType(Object arg, Function<Class<?>, ResolvedJavaType> lookupJavaTypeMethod) {
            // Handle Primitive RuntimeTypes
            if (arg instanceof Boolean) {
                return RuntimeValueBoolean.of((Boolean) arg);
            } else if (arg instanceof Byte) {
                return new RuntimeValueNumber(((Byte) arg));
            } else if (arg instanceof Short) {
                return new RuntimeValueNumber(((Short) arg));
            } else if (arg instanceof Integer) {
                return new RuntimeValueNumber(((Integer) arg));
            } else if (arg instanceof Long) {
                return new RuntimeValueNumber(((Long) arg));
            } else if (arg instanceof Double) {
                return new RuntimeValueNumber(((Double) arg));
            } else if (arg instanceof Float) {
                return new RuntimeValueNumber(((Float) arg));
            } else if (arg instanceof Character) { // todo rework -> shouldn't typecast
                return new RuntimeValueCharacter((Character) arg);
                // Checking if array
                // https://stackoverflow.com/questions/2725533/how-to-see-if-an-object-is-an-array-without-using-reflection
            } else if (arg.getClass().isArray()) {
                return new RuntimeValueArray(arg, this);
            } else if (arg instanceof String) {
                if (lookupJavaTypeMethod != null) {
                    ResolvedJavaType stringType = lookupJavaTypeMethod.apply(String.class);
                    return new RuntimeValueString(stringType, (String) arg);
                }
            }
            // todo Handle Class Instances (other than String and Array)

            return null;
        }
    }
}
