package org.graalvm.compiler.core.test.generating;

import edu.berkeley.cs.jqf.fuzz.*;
import com.pholser.junit.quickcheck.From;
import org.junit.runner.RunWith;
import org.junit.Assume;
import org.graalvm.compiler.api.test.ExportingClassLoader;
import org.graalvm.compiler.core.test.GraalCompilerTest;

import jdk.vm.ci.meta.ResolvedJavaMethod;

import java.io.FileOutputStream;
import java.io.IOException;

@RunWith(JQF.class)
public class SimpleGeneratedCodeTest extends GraalCompilerTest {
    private static final int[] valsToTest = {
            Integer.MIN_VALUE,
            Integer.MIN_VALUE + 1,
            -2,
            -1,
            0,
            1,
            2,
            Integer.MAX_VALUE - 1,
            Integer.MAX_VALUE
    };

    @Fuzz
    public void testWithGeneratedCode(@From(SimpleJavaClassGenerator.class) byte[] code) throws ClassNotFoundException {
        try {
            Class<?> testCls = new ByteArrayClassLoader(ByteArrayClassLoader.class.getClassLoader(), "example.A", code).findClass("example.A");
            FileOutputStream write = new FileOutputStream("./example.A.class");
            write.write(code);
            write.close();
            ResolvedJavaMethod mth = getResolvedJavaMethod(testCls, "asdfghjkl");
            test(mth, null); // BOOM!!!
        } catch (VerifyError v) {
            // invalidate test by assuming no exception has been thrown
            // Also IntelliJ seems to be fine for me catching a VerifyError
            Assume.assumeNoException(v);
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    @Fuzz
    public void testNestedIfs(@From(IfClassGenerator.class) byte[] code) throws ClassNotFoundException {
        try {
            Class<?> testCls = new ByteArrayClassLoader(ByteArrayClassLoader.class.getClassLoader(), "example.Foo", code).findClass("example.Foo");
            FileOutputStream write = new FileOutputStream("./example.Foo.class");
            write.write(code);
            write.close();
            ResolvedJavaMethod mth = getResolvedJavaMethod(testCls, "barBaz");
            for (int i : valsToTest) {
                for (int j : valsToTest) {
		    // signature is: (method, receiver, args...)
                    test(mth, null, i, j); // BOOM!!!
                }
            }
        } catch (VerifyError v) {
            // invalidate test by assuming no exception has been thrown
            // Also IntelliJ seems to be fine for me catching a VerifyError
            Assume.assumeNoException(v);
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    @Fuzz
    public void testIfElse(@From(IfElseClassGenerator.class) byte[] code) throws ClassNotFoundException {
        try {
            Class<?> testCls = new ByteArrayClassLoader(ByteArrayClassLoader.class.getClassLoader(), "example.Ifelse", code).findClass("example.Ifelse");
            FileOutputStream write = new FileOutputStream("./example.Ifelse.class");
            write.write(code);
            write.close();
            ResolvedJavaMethod mth = getResolvedJavaMethod(testCls, "test");
            for (int i : valsToTest) {
                for (int j : valsToTest) {
                    // signature is: (method, receiver, args...)
                    test(mth, null, i, j); // BOOM!!!
                }
            }
        } catch (VerifyError v) {
            // invalidate test by assuming no exception has been thrown
            // Also IntelliJ seems to be fine for me catching a VerifyError
            Assume.assumeNoException(v);
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    @Fuzz
    public void testWhileLoop(@From(WhileClassGenerator.class) byte[] code) throws ClassNotFoundException {
        System.out.println("testWhileLoop");
        try {
            Class<?> testCls = new ByteArrayClassLoader(ByteArrayClassLoader.class.getClassLoader(), "example.B", code).findClass("example.B");
            FileOutputStream write = new FileOutputStream("./example.B.class");
            write.write(code);
            write.close();
            ResolvedJavaMethod mth = getResolvedJavaMethod(testCls, "abc");
            for (int i : valsToTest) {
                for (int j : valsToTest) {
                    // signature is: (method, receiver, args...)
                    test(mth, null, i, j); // BOOM!!!
                }
            }
        } catch (VerifyError v) {
            // invalidate test by assuming no exception has been thrown
            // Also IntelliJ seems to be fine for me catching a VerifyError
            Assume.assumeNoException(v);
        } catch (IOException e){
            System.out.println("throw" + e);
            throw new RuntimeException(e);
        }
        System.out.println("   down");
    }

    @Fuzz
    public void testMethodCall(@From(MethodCallClassGenerator.class) byte[] code) throws ClassNotFoundException {
        try {
            Class<?> testCls = new ByteArrayClassLoader(ByteArrayClassLoader.class.getClassLoader(), "example.C", code).findClass("example.C");
            FileOutputStream write = new FileOutputStream("./example.C.class");
            write.write(code);
            write.close();
            ResolvedJavaMethod mth = getResolvedJavaMethod(testCls, "abcd");
            test(mth, null); // BOOM!!!
        } catch (VerifyError v) {
            // invalidate test by assuming no exception has been thrown
            // Also IntelliJ seems to be fine for me catching a VerifyError
            Assume.assumeNoException(v);
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }
    @Fuzz
    public void testReadWrite(@From(ReadWriteClassGenerator.class) byte[] code) throws ClassNotFoundException {
        try {
            Class<?> testCls = new ByteArrayClassLoader(ByteArrayClassLoader.class.getClassLoader(), "example.D", code).findClass("example.D");
            FileOutputStream write = new FileOutputStream("./example.D.class");
            write.write(code);
            write.close();
            ResolvedJavaMethod mth = getResolvedJavaMethod(testCls, "qwer");
            test(mth, null); // BOOM!!!
        } catch (VerifyError v) {
            // invalidate test by assuming no exception has been thrown
            // Also IntelliJ seems to be fine for me catching a VerifyError
            Assume.assumeNoException(v);
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }
    @Fuzz
    public void testDiffInteger(@From(DiffIntegerClassGenerator.class) byte[] code) throws ClassNotFoundException {
        try {
            Class<?> testCls = new ByteArrayClassLoader(ByteArrayClassLoader.class.getClassLoader(), "example.Integer", code).findClass("example.Integer");
            FileOutputStream write = new FileOutputStream("./example.Integer.class");
            write.write(code);
            write.close();
            ResolvedJavaMethod mth = getResolvedJavaMethod(testCls, "load");
            test(mth, null); // BOOM!!!
        } catch (VerifyError v) {
            // invalidate test by assuming no exception has been thrown
            // Also IntelliJ seems to be fine for me catching a VerifyError
            Assume.assumeNoException(v);
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    public static class ByteArrayClassLoader extends ExportingClassLoader {
        final String className;
        final byte[] code;

        public ByteArrayClassLoader(ClassLoader parent, String className, byte[] code) {
            super(parent);
            this.className = className;
            this.code = code;
        }

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(className)) {
                return defineClass(name, code, 0, code.length);
            } else {
                return super.findClass(name);
            }
        }
    }
}
