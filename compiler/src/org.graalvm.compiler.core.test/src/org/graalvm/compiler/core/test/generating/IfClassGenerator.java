package org.graalvm.compiler.core.test.generating;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.internal.GeometricDistribution;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Label;

import java.util.*;

/*
 * TODO:
 *  - generate locals and initialize them based on the given parameters
 *  - generate if statements, nesting based on the number of local variables
 */

/**
 * Generator for a class that has a method with nested if statements. Based on
 * {@link SimpleJavaClassGenerator}.
 */
public class IfClassGenerator extends Generator<byte[]> {
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

    public IfClassGenerator() {
        super(byte[].class);
    }

    public byte[] generate(SourceOfRandomness r, GenerationStatus s) {
        /*
         * Generate a class that would have the declaration:
         * package example;
         * public class Foo { ... }
         */
        String className = "example/Foo";
        String superName = "java/lang/Object";
        String fileName = "Foo.class";
        int flags = Opcodes.ACC_SUPER | Opcodes.ACC_PUBLIC;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, flags, className, null, superName, null);

        generateMethod(cw, r);

        cw.visitEnd();
        return cw.toByteArray();

    }

    private void generateMethod(ClassWriter cw, SourceOfRandomness r) {
        /*
         * Generate a method that would have the declaration:
         * public static int barBaz(int a, int b) { ... }
         */
        int flags = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        String methodName = "barBaz";
        MethodVisitor mv = cw.visitMethod(flags, methodName, "(II)I", null, null);
        generateCode(r, mv);
        mv.visitEnd();
    }

    private void generateCode(SourceOfRandomness r, MethodVisitor mv) {
        mv.visitCode();

        int numLocals = 2 + geom.sampleWithMean(MEAN_LOCALS_COUNT, r);

        remainingLocals.addAll(Arrays.asList(params));

        if (numLocals > 2) {
            for (int i = 2; i < numLocals; ++i) {
                mv.visitVarInsn(Opcodes.ILOAD, r.choose(params));
                generateLoadCon(r, mv);
                generateBinaryOpOnly(r, mv);
                mv.visitVarInsn(Opcodes.ISTORE, i);
                remainingLocals.add(i);
            }
        }

        generateOps(r, mv, numLocals);
        generateLoadVar(r, mv, numLocals);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(numLocals + 1, numLocals);
    }

    private void generateOps(SourceOfRandomness r, MethodVisitor mv, int slots) {
        if (!remainingLocals.isEmpty()) {
            int varChoice = r.choose(remainingLocals);
            mv.visitVarInsn(Opcodes.ILOAD, varChoice);
            remainingLocals.remove(varChoice);
            Label jmpLabel = new Label();
            mv.visitJumpInsn(getJumpInstr(r), jmpLabel);
            generateOps(r, mv, slots);
            mv.visitLabel(jmpLabel);
        }
        // Change from generateOp to generateNonFinalOp.
        // Using the former was causing ASM to generate code that
        // led to silly stack state transitions (such as immediately going from two
        // ints on the stack to nothing on the stack) and this confused ASM when
        // computing stack map frames.
        while (r.nextBoolean()) {
            generateNonFinalOp(r, mv, slots);
        }
        generateNonFinalOp(r, mv, slots);
    }

    private void generateFinalOp(SourceOfRandomness r, MethodVisitor mv, int slots) {
        if (r.nextBoolean()) {
            generateLoadOnly(r, mv, slots);
        } else {
            generateArithmetic(r, mv, slots);
        }
    }

    private void generateNonFinalOp(SourceOfRandomness r, MethodVisitor mv, int slots) {
        generateFinalOp(r, mv, slots);
        generateStore(r, mv, slots);
    }

    private void generateLoadOnly(SourceOfRandomness r, MethodVisitor mv, int slots) {
        if ((slots > 0) && r.nextBoolean()) {
            generateLoadVar(r, mv, slots);
        } else {
            generateLoadCon(r, mv);
        }
    }

    private void generateArithmetic(SourceOfRandomness r, MethodVisitor mv, int slots) {
        if (r.nextBoolean()) {
            generateBinaryOp(r, mv, slots);
        } else {
            generateNot(r, mv, slots);
        }
    }

    private void generateStore(SourceOfRandomness r, MethodVisitor mv, int slots) {
        mv.visitVarInsn(Opcodes.ISTORE, r.nextInt(slots));
    }

    private void generateLoadCon(SourceOfRandomness r, MethodVisitor mv) {
        int loadConInstr = r.nextInt(256);
        switch (loadConInstr) {
            case Opcodes.ICONST_0:
            case Opcodes.ICONST_1:
            case Opcodes.ICONST_2:
            case Opcodes.ICONST_3:
            case Opcodes.ICONST_4:
            case Opcodes.ICONST_5:
            case Opcodes.ICONST_M1:
                mv.visitInsn(loadConInstr);
                break;
            default:
                mv.visitIntInsn(Opcodes.BIPUSH, r.nextByte(Byte.MIN_VALUE, Byte.MAX_VALUE));
                break;
        }
    }

    private void generateLoadVar(SourceOfRandomness r, MethodVisitor mv, int slots) {
        mv.visitVarInsn(Opcodes.ILOAD, r.nextInt(slots));
    }

    private void generateBinaryOp(SourceOfRandomness r, MethodVisitor mv, int slots) {
        generateLoadOnly(r, mv, slots);
        generateLoadOnly(r, mv, slots);
        int opChoice = r.nextInt(11);
        switch (opChoice) {
            case ADD:
                mv.visitInsn(Opcodes.IADD);
                break;
            case SUB:
                mv.visitInsn(Opcodes.ISUB);
                break;
            case MUL:
                mv.visitInsn(Opcodes.IMUL);
                break;
            case DIV:
                mv.visitInsn(Opcodes.IDIV);
                break;
            case MOD:
                mv.visitInsn(Opcodes.IREM);
                break;
            case AND:
                mv.visitInsn(Opcodes.IAND);
                break;
            case OR:
                mv.visitInsn(Opcodes.IOR);
                break;
            case XOR:
                mv.visitInsn(Opcodes.IXOR);
                break;
            case LSL:
                mv.visitInsn(Opcodes.ISHL);
                break;
            case LSR:
                mv.visitInsn(Opcodes.IUSHR);
                break;
            case ASR:
                mv.visitInsn(Opcodes.ISHR);
                break;
            default:
                /* shouldn't get here */
                break;
        }
    }

    private void generateBinaryOpOnly(SourceOfRandomness r, MethodVisitor mv) {
        int opChoice = r.nextInt(11);
        switch (opChoice) {
            case ADD:
                mv.visitInsn(Opcodes.IADD);
                break;
            case SUB:
                mv.visitInsn(Opcodes.ISUB);
                break;
            case MUL:
                mv.visitInsn(Opcodes.IMUL);
                break;
            case DIV:
                mv.visitInsn(Opcodes.IDIV);
                break;
            case MOD:
                mv.visitInsn(Opcodes.IREM);
                break;
            case AND:
                mv.visitInsn(Opcodes.IAND);
                break;
            case OR:
                mv.visitInsn(Opcodes.IOR);
                break;
            case XOR:
                mv.visitInsn(Opcodes.IXOR);
                break;
            case LSL:
                mv.visitInsn(Opcodes.ISHL);
                break;
            case LSR:
                mv.visitInsn(Opcodes.IUSHR);
                break;
            case ASR:
                mv.visitInsn(Opcodes.ISHR);
                break;
            default:
                /* shouldn't get here */
                break;
        }
    }

    private void generateNot(SourceOfRandomness r, MethodVisitor mv, int slots) {
        generateLoadOnly(r, mv, slots);
        mv.visitInsn(Opcodes.INEG);
    }

    private int getJumpInstr(SourceOfRandomness r) {
        int opChoice = r.nextInt(256);
        switch (opChoice) {
            case Opcodes.IFEQ:
            case Opcodes.IFNE:
            case Opcodes.IFLT:
            case Opcodes.IFGE:
            case Opcodes.IFGT:
            case Opcodes.IFLE:
                return opChoice;
            default:
                return Opcodes.IFEQ;
        }
    }
}
