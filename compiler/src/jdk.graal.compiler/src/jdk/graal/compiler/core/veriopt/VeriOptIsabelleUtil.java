package jdk.graal.compiler.core.veriopt;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.BinaryOpLogicNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ShortCircuitOrNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.calc.AbsNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.IntegerMulHighNode;
import jdk.graal.compiler.nodes.calc.IntegerNormalizeCompareNode;
import jdk.graal.compiler.nodes.calc.IntegerTestNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.NegateNode;
import jdk.graal.compiler.nodes.calc.NotNode;
import jdk.graal.compiler.nodes.calc.OrNode;
import jdk.graal.compiler.nodes.calc.RightShiftNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.SignedDivNode;
import jdk.graal.compiler.nodes.calc.SignedFloatingIntegerDivNode;
import jdk.graal.compiler.nodes.calc.SignedFloatingIntegerRemNode;
import jdk.graal.compiler.nodes.calc.SignedRemNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.graal.compiler.nodes.calc.XorNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.replacements.nodes.BitCountNode;
import jdk.graal.compiler.replacements.nodes.ReverseBytesNode;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PrimitiveConstant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines a collection of definitions and methods to assist in Isabelle translation.
 * */
public class VeriOptIsabelleUtil {

    /**
     * Defines key reasons that encoding or translation failed.
     * */
    private static final HashMap<String, String> exceptions = new HashMap<>();
    static {
        exceptions.put("IREXPR_ENCODING", "trying to encode IRExpr for a node (%s), but %s");
        exceptions.put("IROP_ENCODING",   "trying to encode IROp for a node (%s), but %s");
        exceptions.put("FORMATTING",      "trying to reformat an input, but %s");
    }

    /**
     * Maps GraalVM node classes to their equivalent Isabelle IRBinaryOp type.
     * */
    public static final Map<Class<? extends FloatingNode>, String> IRBinaryOps = new HashMap<>();
    static {
        IRBinaryOps.put(AddNode.class,                      "BinAdd");
        IRBinaryOps.put(SubNode.class,                      "BinSub");
        IRBinaryOps.put(MulNode.class,                      "BinMul");
        IRBinaryOps.put(SignedFloatingIntegerDivNode.class, "BinDiv");
        IRBinaryOps.put(SignedFloatingIntegerRemNode.class, "BinMod");
        IRBinaryOps.put(AndNode.class,                      "BinAnd");
        IRBinaryOps.put(OrNode.class,                       "BinOr");
        IRBinaryOps.put(XorNode.class,                      "BinXor");
        IRBinaryOps.put(ShortCircuitOrNode.class,           "BinShortCircuitOr");
        IRBinaryOps.put(LeftShiftNode.class,                "BinLeftShift");
        IRBinaryOps.put(RightShiftNode.class,               "BinRightShift");
        IRBinaryOps.put(UnsignedRightShiftNode.class,       "BinURightShift");
        IRBinaryOps.put(IntegerEqualsNode.class,            "BinIntegerEquals");
        IRBinaryOps.put(IntegerLessThanNode.class,          "BinIntegerLessThan");
        IRBinaryOps.put(IntegerBelowNode.class,             "BinIntegerBelow");
        IRBinaryOps.put(IntegerTestNode.class,              "BinIntegerTest");
        IRBinaryOps.put(IntegerNormalizeCompareNode.class,  "BinIntegerNormalizeCompare");
        IRBinaryOps.put(IntegerMulHighNode.class,           "BinIntegerMulHigh");
    }

    /**
     * Maps GraalVM node classes to their equivalent Isabelle IRUnaryOp type. <br>
     *
     * Nodes for which {@link #isConvertNode(Node)} have placeholder strings for their input and result bits.
     * */
    private static final Map<Class<? extends FloatingNode>, String> IRUnaryOps = new HashMap<>();
    static {
        IRUnaryOps.put(AbsNode.class,           "UnaryAbs");
        IRUnaryOps.put(NegateNode.class,        "UnaryNeg");
        IRUnaryOps.put(NotNode.class,           "UnaryNot");
        IRUnaryOps.put(LogicNegationNode.class, "UnaryLogicNegation");
        IRUnaryOps.put(NarrowNode.class,        "UnaryNarrow %s %s");
        IRUnaryOps.put(SignExtendNode.class,    "UnarySignExtend %s %s");
        IRUnaryOps.put(ZeroExtendNode.class,    "UnaryZeroExtend %s %s");
        IRUnaryOps.put(IsNullNode.class,        "UnaryIsNull");
        IRUnaryOps.put(ReverseBytesNode.class,  "UnaryReverseBytes");
        IRUnaryOps.put(BitCountNode.class,      "UnaryBitCount");
    }

    /**
     * Defines the constructors for each Isabelle {@code IRExpr} which is output by TreeToGraph's rep inductive
     * definition.
     * */
    private static final Map<String, String> IRExprs = new HashMap<>();
    static {
        IRExprs.put("UnaryExpr",       "UnaryExpr (%s) (%s)");
        IRExprs.put("BinaryExpr",      "BinaryExpr (%s) (%s) (%s)");
        IRExprs.put("ConditionalExpr", "ConditionalExpr (%s) (%s) (%s)");
        IRExprs.put("ParameterExpr",   "ParameterExpr %s (%s)");
        IRExprs.put("LeafExpr",        "LeafExpr %s (%s)");
        IRExprs.put("ConstantExpr",    "ConstantExpr %s");
    }

    /**
     * Returns whether the given {@code node} has a corresponding Isabelle {@code IRExpr} representation. <br>
     *
     * Based on the rep inductive definition in TreeToGraph.
     *
     * @param node the node whose type is being checked.
     * @return {@code true} if the given {@code node} has a corresponding Isabelle {@code IRExpr} representation, else
     *         {@code false}.
     * */
    public static boolean hasIRExprRep(Node node) {
        // No IRExpr exists for a null node
        if (node == null) {
            return false;
        }

        // Check if node is part of a collection where all nodes have IRExpr representations
        if (isIRUnaryOp(node) || isIRBinaryOp(node) || isConvertNode(node) || isPreevaluatedNode(node)) {
            return true;
        }

        // Check remaining nodes represented in TreeToGraph's rep
        return (node instanceof ConstantNode)    || (node instanceof ParameterNode)  ||
               (node instanceof ConditionalNode) || (node instanceof ValueProxyNode) || (node instanceof PiNode);
    }

    /**
     * Returns whether the given {@code node} is an {@link IntegerConvertNode}.
     *
     * @param node the node being checked.
     * @return {@code true} if the {@code node} is an {@code IntegerConvertNode}, else {@code false}.
     * */
    public static boolean isConvertNode(Node node) {
        return (node instanceof IntegerConvertNode);
    }

    /**
     * Returns whether the given {@code node} has a corresponding Isabelle IRBinaryOp definition.
     *
     * @param node the node being checked.
     * @return {@code true} if the given {@code node} has a corresponding Isabelle IRBinaryOp definition, else
     *         {@code false}.
     * */
    public static boolean isIRBinaryOp(Node node) {
        return (IRBinaryOps.get(node.getClass()) != null);
    }

    /**
     * Returns whether the given {@code node} has a corresponding Isabelle IRUnaryOp definition.
     *
     * @param node the node being checked.
     * @return {@code true} if the given {@code node} has a corresponding Isabelle IRUnaryOp definition, else
     *         {@code false}.
     * */
    public static boolean isIRUnaryOp(Node node) {
        return (IRUnaryOps.get(node.getClass()) != null);
    }

    /**
     * Returns whether the given {@code node} is evaluated during control-flow. <br>
     *
     * Mimics TreeToGraph's {@code is_preevaluated} function.
     *
     * @param node the node whose type is being checked.
     * @return {@code true} if the given {@code node} is evaluated during control-flow, else {@code false}.
     * */
    public static boolean isPreevaluatedNode(Node node) {
        return (node instanceof InvokeNode)              ||
               (node instanceof InvokeWithExceptionNode) ||
               (node instanceof NewInstanceNode)         ||
               (node instanceof LoadFieldNode)           ||
               (node instanceof SignedDivNode)           ||
               (node instanceof SignedRemNode)           ||
               (node instanceof ValuePhiNode)            ||
               (node instanceof BytecodeExceptionNode)   ||
               (node instanceof NewArrayNode)            ||
               (node instanceof ArrayLengthNode)         ||
               (node instanceof LoadIndexedNode)         ||
               (node instanceof StoreIndexedNode);
    }

    /**
     * Returns the Isabelle {@code IRExpr} representation for the given {@code node}, if {@link #hasIRExprRep(Node)}. <br>
     *
     * Based on the rep inductive definition in TreeToGraph.
     *
     * @param node the node whose Isabelle {@code IRExpr} representation is being returned.
     * @param encodingPhis whether {@link PhiNode}s are being encoded as ParameterExprs.
     * @param phiIndexes a mapping from {@code PhiNode}s to their index as a {@link ParameterNode}, if
     *                   {@code encodingPhis}.
     * @return the {@code IRExpr} representation of the given {@code node}.
     * @throws RuntimeException if no Isabelle {@code IRExpr} encoding exists for the given {@code node}.
     * */
    public static String encodeIRExpr(Node node, boolean encodingPhis, HashMap<PhiNode, Integer> phiIndexes) {
        if (node instanceof LogicConstantNode) {
            // Pre-emptively transform LogicConstantNodes into ConstantNodes, as they are known as such by Isabelle
            return encodeIRExpr(asConstantNode((LogicConstantNode) node), encodingPhis, phiIndexes);
        }

        if (!hasIRExprRep(node)) {
            throw new RuntimeException(String.format(exceptions.get("IREXPR_ENCODING"),
                    (node == null) ? "[null]" : node, "it does not have a corresponding Isabelle representation"));
        }

        // Translate the node based on its type
        if (isConvertNode(node)) {
            String inputBits = String.valueOf(((IntegerConvertNode<?>) node).getInputBits());
            String resultBits = String.valueOf(((IntegerConvertNode<?>) node).getResultBits());
            String operator = String.format(asIRUnaryOp(node), inputBits, resultBits);

            String value = encodeIRExpr(((IntegerConvertNode<?>) node).getValue(), encodingPhis, phiIndexes);
            return String.format(IRExprs.get("UnaryExpr"), operator, value);
        }

        if (isIRUnaryOp(node)) {
            // Value stored by this unary operator
            String encodedValue = "";

            // Handle NullNode, LogicNegationNode and UnaryNode separately
            if (node instanceof IsNullNode) {
                encodedValue = encodeIRExpr(((IsNullNode) node).getValue(), encodingPhis, phiIndexes);
            } else if (node instanceof LogicNegationNode) {
                encodedValue = encodeIRExpr(((LogicNegationNode) node).getValue(), encodingPhis, phiIndexes);
            } else {
                encodedValue = encodeIRExpr(((UnaryNode) node).getValue(), encodingPhis, phiIndexes);
            }

            return String.format(IRExprs.get("UnaryExpr"), asIRUnaryOp(node), encodedValue);
        }

        if (isIRBinaryOp(node)) {
            // Values stored by this binary operator
            String x = "";
            String y = "";

            // Handle ShortCircuitOrNode, BinaryOpLogicNode and BinaryNode separately
            if (node instanceof ShortCircuitOrNode) {
                x = encodeIRExpr(((ShortCircuitOrNode) node).getX(), encodingPhis, phiIndexes);
                y = encodeIRExpr(((ShortCircuitOrNode) node).getY(), encodingPhis, phiIndexes);
            } else if (node instanceof BinaryOpLogicNode) {
                x = encodeIRExpr(((BinaryOpLogicNode) node).getX(), encodingPhis, phiIndexes);
                y = encodeIRExpr(((BinaryOpLogicNode) node).getY(), encodingPhis, phiIndexes);
            } else {
                x = encodeIRExpr(((BinaryNode) node).getX(), encodingPhis, phiIndexes);
                y = encodeIRExpr(((BinaryNode) node).getY(), encodingPhis, phiIndexes);
            }

            return String.format(IRExprs.get("BinaryExpr"), asIRBinaryOp(node), x, y);
        }

        if (node instanceof PhiNode && encodingPhis) {
            // Handle phis separately from pre-evaluated nodes due to potential phi encoding

            if (phiIndexes == null || !phiIndexes.containsKey(node)) {
                // No phi indexes were provided, or they don't contain an index mapping for this node
                String suffix = (phiIndexes == null) ? "no phi indexes were provided" : "this phi has no index value";
                throw new RuntimeException(String.format(exceptions.get("IREXPR_ENCODING"),
                        node, "phis are being encoded and " + suffix));
            }

            // Phis must become parameters to their function calls
            Stamp phiStamp = ((PhiNode) node).stamp(NodeView.DEFAULT);
            int index = phiIndexes.get(node);
            return encodeIRExpr(new ParameterNode(index, StampPair.create(phiStamp, phiStamp)), true, phiIndexes);
        }

        if (isPreevaluatedNode(node)) {
            String stamp = VeriOptStampEncoder.encodeStamp(((ValueNode) node).stamp(NodeView.DEFAULT));
            return String.format(IRExprs.get("LeafExpr"), asNodeID(node), stamp);
        }

        if (node instanceof ConstantNode) {
            String constant = asIsabelleConstant(getConstantValue((ConstantNode) node));
            return String.format(IRExprs.get("ConstantExpr"), constant);
        }

        if (node instanceof ParameterNode) {
            String index = String.valueOf(((ParameterNode) node).index());
            String stamp = VeriOptStampEncoder.encodeStamp(((ParameterNode) node).stamp((NodeView.DEFAULT)));
            return String.format(IRExprs.get("ParameterExpr"), index, stamp);
        }

        if (node instanceof ConditionalNode) {
            String condition = encodeIRExpr(((ConditionalNode) node).condition(), encodingPhis, phiIndexes);
            String trueBranch = encodeIRExpr(((ConditionalNode) node).trueValue(), encodingPhis, phiIndexes);
            String falseBranch = encodeIRExpr(((ConditionalNode) node).falseValue(), encodingPhis, phiIndexes);
            return String.format(IRExprs.get("ConditionalExpr"), condition, trueBranch, falseBranch);
        }

        if (node instanceof ValueProxyNode) {
            return encodeIRExpr(((ValueProxyNode) node).value(), encodingPhis, phiIndexes);
        }

        if (node instanceof PiNode) {
            return encodeIRExpr(((PiNode) node).object(), encodingPhis, phiIndexes);
        }

        // No encoding rule was defined for this node
        throw new RuntimeException(String.format(exceptions.get("IREXPR_ENCODING"), node, "no encoding rule exists"));
    }

    /**
     * Generates and returns the given {@code nodes} encoded as {@code IRExpr}s.
     *
     * @param nodes the nodes being encoded.
     * @param encodingPhis whether {@link PhiNode}s are being encoded as ParameterExprs.
     * @param phiIndexes a mapping from {@code PhiNode}s to their index as a {@link ParameterNode}, if
     *                   {@code encodingPhis}.
     * @return the given {@code nodes} encoded into their {@code IRExpr} representations.
     * */
    public static ArrayList<String> encodeIRExprs(ArrayList<Node> nodes, boolean encodingPhis,
                                                  HashMap<PhiNode, Integer> phiIndexes) {
        ArrayList<String> encodedIRExprs = new ArrayList<>();

        // Iterate over the nodes and store their IRExpr representation
        for (Node node : nodes) {
            String encodedNode = VeriOptIsabelleUtil.encodeIRExpr(node, encodingPhis, phiIndexes);
            encodedIRExprs.add(encodedNode);
        }

        return encodedIRExprs;
    }

    /**
     * Transforms the given {@link LogicConstantNode} into a {@link ConstantNode} storing the same value. <br>
     *
     * The Isabelle {@code IRNode} definition does not contain {@code LogicConstantNode}s, and hence they do not have a
     * corresponding {@code IRExpr} representation. Instead, their stored value is translated directly into an Isabelle
     * constant using {@link VeriOptNodeBuilder#value(Object)} throughout the IRGraph encoding
     * ({@link VeriOptGraphTranslator#writeNodeArray(Graph)}). <br>
     *
     * To simulate this, {@code LogicConstantNode}s are pre-emptively transformed into {@code ConstantNode}s prior to
     * being evaluated inside {@link #encodeIRExpr(Node, boolean, HashMap)}.
     *
     * @param node the {@code LogicConstantNode} being transformed into an equivalent {@code ConstantNode}.
     * @return the {@code ConstantNode} equivalent for the given {@code LogicConstantNode}.
     * */
    private static ConstantNode asConstantNode(LogicConstantNode node) {
        return ConstantNode.forBoolean(node.getValue());
    }

    /**
     * Wrapper method for {@link #asIsabelleIROp(Node, boolean)}, where {@code isBinaryOp} = {@code false}.
     * */
    public static String asIRUnaryOp(Node node) {
        return asIsabelleIROp(node, false);
    }

    /**
     * Wrapper method for {@link #asIsabelleIROp(Node, boolean)}, where {@code isBinaryOp} = {@code true}.
     * */
    public static String asIRBinaryOp(Node node) {
        return asIsabelleIROp(node, true);
    }

    /**
     * Encodes the given {@code node} into its Isabelle IROp ({@code IRBinaryOp} or {@code IRUnaryOp}) type, as defined
     * in the {@link #IRBinaryOps} or {@link #IRUnaryOps} mapping (respectively), if it has one.
     *
     * @param node the node being encoded as its Isabelle IROp type.
     * @param isBinaryOp indicates whether the provided {@code node} produces an Isabelle {@code IRBinaryOp}
     *                   ({@code true}) or {@code IRUnaryOp} ({@code false}).
     * @return the Isabelle IROp definition for the given {@code node}.
     * @throws RuntimeException if the given {@code node} is {@code null}, or it is not defined in the expected mapping.
     * */
    private static String asIsabelleIROp(Node node, boolean isBinaryOp) {
        // Cannot translate a null node
        if (node == null) {
            throw new RuntimeException(String.format(exceptions.get("IROP_ENCODING"), "[null]", "null node provided"));
        }

        // Retrieve the Isabelle IROp
        String op = (isBinaryOp ? IRBinaryOps : IRUnaryOps).get(node.getClass());

        if (op == null) {
            // Node class is not defined in the expected mapping
            String isabelleOpType = "IR" + (isBinaryOp ? "Binary" : "Unary") + "Op";
            String message = "it does not have a corresponding Isabelle" + isabelleOpType + "type";
            throw new RuntimeException(String.format(exceptions.get("IROP_ENCODING"), node, message));
        }

        return op;
    }

    /**
     * Returns the value stored by the given {@link ConstantNode} as a Java {@code Object}, if it's a value that can be
     * encoded in Isabelle.
     *
     * @param node the {@code ConstantNode} whose value is being extracted.
     * @return the given {@code node}'s value as an {@code Object}.
     * @throws IllegalArgumentException if the value stored by this {@code node} cannot be represented in Isabelle.
     * */
    public static Object getConstantValue(ConstantNode node) {
        Constant value = node.getValue();

        if (value instanceof PrimitiveConstant primitive) {
            return primitive.asBoxedPrimitive();
        }

        if (value instanceof JavaConstant constant && constant.isNull()) {
            return null;
        }

        // We can't represent the value in Isabelle yet
        throw new IllegalArgumentException("constant type " + value + " (" + value.getClass().getName() + ") not implemented yet.");
    }

    /**
     * Encodes and returns the given {@code object} as an Isabelle constant.
     *
     * @param object the constant as an {@code Object} instance.
     * @return the Isabelle-friendly syntax for the given {@code object}.
     * */
    public static String asIsabelleConstant(Object object) {
        return VeriOptValueEncoder.value(object, false, false);
    }

    /**
     * Returns the given {@code node}'s ID in a graph.
     *
     * @param node the node whose ID is being retrieved.
     * @return the ID for the given {@code node}.
     * */
    public static String asNodeID(Node node) {
        return node.toString(Verbosity.Id);
    }

    /**
     * Defines a set of functions to assist with formatting.
     * */
    public static final class Formatting {

        /**
         * Returns the given {@code input} {@code String} with any placeholders ("%s") replaced by the provided
         * {@code arguments}. See {@link String#format(String, Object...)} for more details.
         *
         * @param input the input whose placeholders ("%s") are being replaced by the provided {@code arguments}.
         * @param arguments the arguments for the {@code input}.
         * @return the original {@code input} with all placeholders replaced by the given {@code arguments}.
         * @throws RuntimeException if the {@code input} or {@code arguments} are {@code null}.
         * */
        public static String formatPlaceholderString(String input, String... arguments) {
            // Ensure input & arguments aren't null
            if (input == null || arguments == null) {
                throw new RuntimeException(String.format(exceptions.get("FORMATTING"),
                        "null placeholder string or arguments were provided"));
            }

            // Insert any provided arguments
            return String.format(input, Arrays.stream(arguments).toArray());
        }

        /**
         * Removes the last instance of the given {@code symbol} from the given {@code builder}.
         *
         * @param builder the {@code StringBuilder} being modified.
         * @param symbol the symbol whose last instance will be removed from the {@code builder}.
         * */
        public static void removeLastInstanceOfSymbol(StringBuilder builder, String symbol) {
            builder.deleteCharAt(builder.lastIndexOf(symbol));
        }

        /**
         * Removes all characters from the last instance of the given {@code symbol} to the end of the {@code builder}
         * (inclusive). In effect, the last instance of the given {@code symbol} becomes the end of the {@code builder}.
         *
         * @param builder the {@code StringBuilder} being modified.
         * @param symbol the symbol whose last instance will become the new end of the {@code builder}.
         * */
        public static void removeTrailingFromLastSymbol(StringBuilder builder, String symbol) {
            builder.setLength(builder.lastIndexOf(symbol));
        }

        /**
         * Wraps the input {@code items} in an array syntax, separated by the provided {@code separator}. The
         * resultant output is:
         *
         * <pre>
         *     [items[0]{separator} items[1]{separator} ... {separator} items[n-1]]
         * </pre>
         *
         * @param separator the symbol which will separate each of the given {@code items}.
         * @param items the inputs being wrapped in an array syntax.
         * @return the input {@code items} in an array syntax separated by the {@code separator}.
         * */
        public static String toArraySyntax(char separator, Iterable<?> items) {
            StringBuilder array = new StringBuilder();

            // Open the array
            array.append("[");

            // Populate the array
            for (Object item : items) {
                String string = item.toString();
                array.append(string);
                array.append(separator);
                array.append(" ");
            }

            if (array.length() != 1) {
                // Items were added; remove the last separator & space
                removeTrailingFromLastSymbol(array, String.valueOf(separator));
            }

            // Close the array
            array.append("]");
            return array.toString();
        }
    }

    /**
     * Defines a set of functions to assist with Isabelle syntax encoding.
     * */
    public static final class Syntax {

        /**
         * Wraps the {@code input} string in Isabelle string quotation marks. The resultant output is:
         *
         * <pre>
         *     ''input''
         * </pre>
         *
         * @param input the input being transformed into an Isabelle-syntax string.
         * @return the {@code input} string as an Isabelle-syntax string.
         * */
        public static String toIsabelleString(String input) {
            return "''" + input + "''";
        }

        /**
         * Wraps the input {@code strings} in an Isabelle array syntax. Assumes that the provided {@code strings} are in
         * an Isabelle-friendly format. The resultant output is:
         *
         * <pre>
         *     [strings[0], strings[1], ... , strings[n-1]]
         * </pre>
         *
         * @param strings the input being wrapped in an Isabelle-syntax array.
         * @return the input {@code strings} as an Isabelle-syntax array.
         * */
        public static String toIsabelleArray(List<String> strings) {
            return Formatting.toArraySyntax(',', strings);
        }
    }
}