package org.graalvm.compiler.core.test.generating;

import edu.berkeley.cs.jqf.fuzz.*;
import com.pholser.junit.quickcheck.From;
import org.junit.runner.RunWith;
import org.junit.Assume;
import org.graalvm.compiler.api.test.ExportingClassLoader;
import org.graalvm.compiler.core.test.GraalCompilerTest;

import jdk.vm.ci.meta.ResolvedJavaMethod;

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
            ResolvedJavaMethod mth = getResolvedJavaMethod(testCls, "asdfghjkl");
            test(mth, null); // BOOM!!!
        } catch (VerifyError v) {
            // invalidate test by assuming no exception has been thrown
            // Also IntelliJ seems to be fine for me catching a VerifyError
            Assume.assumeNoException(v);
        }
    }

    @Fuzz
    public void testNestedIfs(@From(IfClassGenerator.class) byte[] code) throws ClassNotFoundException {
        try {
            Class<?> testCls = new ByteArrayClassLoader(ByteArrayClassLoader.class.getClassLoader(), "example.Foo", code).findClass("example.Foo");
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
