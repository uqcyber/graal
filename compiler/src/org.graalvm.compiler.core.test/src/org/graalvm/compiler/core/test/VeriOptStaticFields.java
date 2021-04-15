package org.graalvm.compiler.core.test;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.options.OptionValues;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * An object representing all the declared static fields and their values for a class
 */
public class VeriOptStaticFields implements Iterable<Map.Entry<Field, Object>> {

    private final HashMap<Field, Object> fields = new HashMap<>();
    
    /**
     * Dumps the declared static fields of a class and their values into an object for later use
     * @param clazz The class declaring the static fields
     * @return The static fields and their values
     */
    public static VeriOptStaticFields getStaticFields(Class<?> clazz) {
        VeriOptStaticFields staticFields = new VeriOptStaticFields();
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                try {
                    field.setAccessible(true); // Let us access private fields
                    staticFields.fields.put(field, field.get(null));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        
        return staticFields;
    }

    @Override
    public Iterator<Map.Entry<Field, Object>> iterator() {
        return fields.entrySet().iterator();
    }

    public StructuredGraph toGraph(OptionValues initialOptions, DebugContext debugContext, MetaAccessProvider metaAccessProvider) {
        StructuredGraph graph = new StructuredGraph.Builder(initialOptions, debugContext).name("").build();
        
        StartNode startNode = graph.start();
        FrameState frameState = new FrameState(BytecodeFrame.BEFORE_BCI);
        ReturnNode returnNode = new ReturnNode(null);

        graph.add(frameState);

        StoreFieldNode previousStoreFieldNode = null;
        for (Map.Entry<Field, Object> entry : this) {

            JavaConstant constant = JavaConstant.forBoxedPrimitive(entry.getValue());
            
            if (constant == null) {
                System.out.println("Cannot handle non-primitive field: " + entry.getKey().getName() + " = " + entry.getValue() + (entry.getValue() != null ? " (" + entry.getValue().getClass().getName() + ")" : ""));
                continue;
            }
            
            ConstantNode constantNode = new ConstantNode(constant, StampFactory.forConstant(constant));
            constantNode = graph.addOrUnique(constantNode);
            
            StoreFieldNode storeFieldNode = new StoreFieldNode(null, metaAccessProvider.lookupJavaField(entry.getKey()), constantNode);
            storeFieldNode.setStamp(StampFactory.forConstant(constant));
            graph.add(storeFieldNode);
            
            if (previousStoreFieldNode != null) {
                previousStoreFieldNode.setNext(storeFieldNode);
            }
            
            if (startNode.next() == null) {
                startNode.setNext(storeFieldNode);
            }

            previousStoreFieldNode = storeFieldNode;
        }

        graph.add(returnNode);
        
        if (previousStoreFieldNode != null) {
            previousStoreFieldNode.setNext(returnNode);
        } else {
            startNode.setNext(returnNode);
        }
        
        startNode.setStateAfter(frameState);
        
        return graph;
    }
}
