package org.graalvm.compiler.core.test.generating;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.internal.GeometricDistribution;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.*;
import java.util.*;

/**
 * Generator for a class that has a method with nested ifelse statements. Based on
 * {@link SimpleJavaClassGenerator}. Tree API
 */
public class MethodCallClassGenerator extends Generator<byte[]> {
    private static GeometricDistribution geom = new GeometricDistribution();

    private Set<Integer> remainingLocals = new HashSet<>();

    private final Integer[] params = {0, 1};

    // Constants
    private static double MEAN_LOCALS_COUNT = 3;

    // Internal binary opcode repns
    private static final int ADD = 0;
    private static final int SUB = 1;
    private static final int MUL = 2;
    private static final int DIV = 3;
    private static final int MOD = 4;
    private static final int AND = 5;
    private static final int OR = 6;
    private static final int XOR = 7;
    private static final int LSL = 8;
    private static final int LSR = 9;
    private static final int ASR = 10;

    public MethodCallClassGenerator() {
        super(byte[].class);
    }

    public byte[] generate(SourceOfRandomness r, GenerationStatus s) {
        /*
         * Generate a class that would have the declaration:
         * package example;
         * public class C { ... }
         */
        ClassNode cn = new ClassNode();
        cn.access = Opcodes.ACC_SUPER | Opcodes.ACC_PUBLIC;
        cn.name = "example/C";
        cn.version = Opcodes.V17;
        cn.superName = "java/lang/Object";

        generateMethod(cn, r);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private void generateMethod(ClassNode cn, SourceOfRandomness r) {
        /*
         * Generate a method that would have the declaration:
         * public static int ifElse(int a, int b) { ... }
         */
        int flags = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        String methodName = "abcd";
        MethodNode mn = new MethodNode(flags, methodName, "(II)I", null, null);
        cn.methods.add(mn);
        generateCode(r, mn);
    }

    private void generateCode(SourceOfRandomness r, MethodNode mn){

        InsnList il = mn.instructions;
        int numLocals = 2 + geom.sampleWithMean(MEAN_LOCALS_COUNT, r);

        remainingLocals.addAll(Arrays.asList(params));

        if (numLocals > 2) {
            for (int i = 2; i < numLocals; ++i) {
                il.add(new VarInsnNode(Opcodes.ILOAD,r.choose(params)));
                generateLoadCon(r, il);
                generateBinaryOpOnly(r, il);
                il.add(new VarInsnNode(Opcodes.ISTORE,i));
                remainingLocals.add(i);
            }
        }

        generateOps(r, il, numLocals);
        generateMethodCall(il);
        generateLoadVar(r, il, numLocals);
        il.add(new InsnNode(Opcodes.IRETURN));
        mn.maxStack = numLocals + 1;
        mn.maxLocals = numLocals;
    }

    private void generateOps(SourceOfRandomness r, InsnList il, int slots) {

        if (!remainingLocals.isEmpty()) {
            int varChoice = r.choose(remainingLocals);
            il.add(new VarInsnNode(Opcodes.ILOAD, varChoice));
            remainingLocals.remove(varChoice);
            generateOps(r, il, slots);
        }
        while (r.nextBoolean()) {
            generateNonFinalOp(r, il, slots);
        }
        generateNonFinalOp(r, il, slots);
    }

    private void generateMethodCall(InsnList il) {
        // Load parameters for Math.min(int a, int b)
        il.add(new VarInsnNode(Opcodes.ILOAD, 0));  // Load the first argument from local variable 0
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));  // Load the second argument from local variable 1

        // Add a method call to Math.min(int a, int b)
        il.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,       // opcode for calling a static method
                "java/lang/Math",           // class containing the method
                "min",                      // name of the method
                "(II)I",                    // descriptor of the method
                false                       // is interface method? (false because Math is a class)
        ));

        // Store result in a local variable, let's say in local variable 2
        il.add(new VarInsnNode(Opcodes.ISTORE, 2));
    }


    private void generateFinalOp(SourceOfRandomness r, InsnList il, int slots) {
        if (r.nextBoolean()) {
            generateLoadOnly(r, il, slots);
        } else {
            generateArithmetic(r, il, slots);
        }
    }

    private void generateArithmetic(SourceOfRandomness r, InsnList il, int slots) {
        if (r.nextBoolean()) {
            generateBinaryOp(r, il, slots);
        } else {
            generateNot(r, il, slots);
        }
    }

    private void generateStore(SourceOfRandomness r, InsnList il, int slots) {
        il.add(new VarInsnNode(Opcodes.ISTORE, r.nextInt(slots)));
    }

    private void generateNot(SourceOfRandomness r, InsnList il, int slots) {
        generateLoadOnly(r, il, slots);
        il.add(new InsnNode(Opcodes.INEG));
    }

    private void generateBinaryOp(SourceOfRandomness r, InsnList il, int slots) {
        generateLoadOnly(r, il, slots);
        generateLoadOnly(r, il, slots);
        int opChoice = r.nextInt(11);
        switch (opChoice) {
            case ADD:
                il.add(new InsnNode(Opcodes.IADD));
                break;
            case SUB:
                il.add(new InsnNode(Opcodes.ISUB));
                break;
            case MUL:
                il.add(new InsnNode(Opcodes.IMUL));
                break;
            case DIV:
                il.add(new InsnNode(Opcodes.IDIV));
                break;
            case MOD:
                il.add(new InsnNode(Opcodes.IREM));
                break;
            case AND:
                il.add(new InsnNode(Opcodes.IAND));
                break;
            case OR:
                il.add(new InsnNode(Opcodes.IOR));
                break;
            case XOR:
                il.add(new InsnNode(Opcodes.IXOR));
                break;
            case LSL:
                il.add(new InsnNode(Opcodes.ISHL));
                break;
            case LSR:
                il.add(new InsnNode(Opcodes.IUSHR));
                break;
            case ASR:
                il.add(new InsnNode(Opcodes.ISHR));
                break;
            default:
                /* shouldn't get here */
                break;
        }
    }

    private void generateLoadOnly(SourceOfRandomness r, InsnList il, int slots) {
        if ((slots > 0) && r.nextBoolean()) {
            generateLoadVar(r, il, slots);
        } else {
            generateLoadCon(r, il);
        }
    }

    private void generateNonFinalOp(SourceOfRandomness r, InsnList il, int slots) {
        generateFinalOp(r, il, slots);
        generateStore(r, il, slots);
    }


    private void generateLoadVar(SourceOfRandomness r, InsnList il, int slots) {
        il.add(new VarInsnNode(Opcodes.ILOAD, r.nextInt(slots)));
    }



    private void generateLoadCon(SourceOfRandomness r, InsnList il){
        int loadConInstr = r.nextInt(256);
        switch (loadConInstr) {
            case Opcodes.ICONST_0:
            case Opcodes.ICONST_1:
            case Opcodes.ICONST_2:
            case Opcodes.ICONST_3:
            case Opcodes.ICONST_4:
            case Opcodes.ICONST_5:
            case Opcodes.ICONST_M1:
                il.add(new InsnNode(loadConInstr));
                break;
            default:
                il.add(new IntInsnNode(Opcodes.BIPUSH, r.nextByte(Byte.MIN_VALUE, Byte.MAX_VALUE)));
                break;
        }
    }

    private void generateBinaryOpOnly(SourceOfRandomness r, InsnList il){
        int opChoice = r.nextInt(11);
        switch (opChoice) {
            case ADD:
                il.add(new InsnNode(Opcodes.IADD));
                break;
            case SUB:
                il.add(new InsnNode(Opcodes.ISUB));
                break;
            case MUL:
                il.add(new InsnNode(Opcodes.IMUL));
                break;
            case DIV:
                il.add(new InsnNode(Opcodes.IDIV));
                break;
            case MOD:
                il.add(new InsnNode(Opcodes.IREM));
                break;
            case AND:
                il.add(new InsnNode(Opcodes.IAND));
                break;
            case OR:
                il.add(new InsnNode(Opcodes.IOR));
                break;
            case XOR:
                il.add(new InsnNode(Opcodes.IXOR));
                break;
            case LSL:
                il.add(new InsnNode(Opcodes.ISHL));
                break;
            case LSR:
                il.add(new InsnNode(Opcodes.IUSHR));
                break;
            case ASR:
                il.add(new InsnNode(Opcodes.ISHR));
                break;
            default:
                /* shouldn't get here */
                break;
        }

    }
}
