package org.graalvm.compiler.debug.interpreter.value.type;

import org.graalvm.compiler.debug.interpreter.value.RuntimeValue;

public class RuntimeValueNumber extends RuntimeValue {

    // todo consider all subclasses of Number:
    // AtomicDouble, AtomicInteger, AtomicLong, BigDecimal, BigFraction, BigInteger, Byte,
    // Decimal64, Double, Float, Fraction, Integer, Long, Short, Striped64, UnsignedInteger,
    // UnsignedLong

    // todo consider using BigDecimal for float and double

    private Number value;

    public RuntimeValueNumber(Number number) {
        this.value = number;
    }

    public Number getValue() {
        return value;
    }


    @Override
    public Object toObject() {
        return value;
    }

    @Override
    public Class<?> getClazz() {
        return value.getClass();
    }

    @Override
    public String toString() {
        return super.toString() + "(" + value + ")";
    }

    @Override
    public Boolean getBoolean() {
        return asRuntimeBoolean().getBoolean();
    }


    // Note, now value is not final!
    public void coerceValue(int size, boolean isSigned) {
        switch (size) {
            // Signed coercion
            case (32): {
                value = value.intValue();
                break;
            }
            case (64): {
                if (!isSigned) { // Since Java 8, int and long can be unsigned.
                    value = Integer.toUnsignedLong((Integer) value);
                } else {
                    value = value.longValue();
                    break;
                }
            }
        }
    }

    // TODO: fix this
    // TODO: use ArithmeticOpTable
    public static RuntimeValueNumber coerceValue(RuntimeValueNumber value, int inputBits, int resultBits, boolean isSigned) {
        switch (resultBits) {
            // Signed coercion
            case (32): {
                return new RuntimeValueNumber(value.value.intValue());
            }
            case (64): {
                if (!isSigned) { // Since Java 8, int and long can be unsigned.
                    return new RuntimeValueNumber(Integer.toUnsignedLong((Integer) value.value));
                } else {
                    return new RuntimeValueNumber(value.value.longValue());
                }
            }
        }
        assert false;
        return null;
    }

    private void unary_number_converter(RuntimeValue value, int inputBits, int resultBits, boolean isSigned) {
        // Todo naive implementation, checks the result bits of the zero extend and coerces
        // RTNumber to 'fit'
        if (inputBits < resultBits) { // todo should likely work with stamps instead
            if (value instanceof RuntimeValueNumber) { // todo currently only coerces to long, otherwise
                // no effect
                ((RuntimeValueNumber) value).coerceValue(resultBits, isSigned);
            }
        }
    }

    public RuntimeValue asRuntimeBoolean() {
        if (value instanceof Double) {
            return RuntimeValueBoolean.of(value.doubleValue() != 0.0d);
        } else if (value instanceof Float) {
            return RuntimeValueBoolean.of(value.floatValue() != 0.0f);
        } else if (value instanceof Long) {
            return RuntimeValueBoolean.of(value.longValue() != 0L);
        } else {
            return RuntimeValueBoolean.of(value.intValue() != 0);
        }
    }

    public static RuntimeValueNumber add(RuntimeValueNumber x, RuntimeValueNumber y) {
        Number a = x.getValue();
        Number b = y.getValue();

        if (a instanceof Double || b instanceof Double) {
            return new RuntimeValueNumber(a.doubleValue() + b.doubleValue());
        } else if (a instanceof Float || b instanceof Float) {
            return new RuntimeValueNumber(a.floatValue() + b.floatValue());
        } else if (a instanceof Long || b instanceof Long) {
            return new RuntimeValueNumber(a.longValue() + b.longValue());
        } else if (a instanceof Byte || b instanceof Byte) {
            return new RuntimeValueNumber(a.byteValue() + b.byteValue());
        } else if (a instanceof Short || b instanceof Short) {
            return new RuntimeValueNumber(a.shortValue() + b.shortValue());
        } else {
            return new RuntimeValueNumber(a.intValue() + b.intValue());
        }
    }

    public static RuntimeValueNumber sub(RuntimeValueNumber x, RuntimeValueNumber y) {
        Number a = x.getValue();
        Number b = y.getValue();

        if (a instanceof Double || b instanceof Double) {
            return new RuntimeValueNumber(a.doubleValue() - b.doubleValue());
        } else if (a instanceof Float || b instanceof Float) {
            return new RuntimeValueNumber(a.floatValue() - b.floatValue());
        } else if (a instanceof Long || b instanceof Long) {
            return new RuntimeValueNumber(a.longValue() - b.longValue());
        } else if (a instanceof Byte || b instanceof Byte) {
            return new RuntimeValueNumber(a.byteValue() - b.byteValue());
        } else if (a instanceof Short || b instanceof Short) {
            return new RuntimeValueNumber(a.shortValue() - b.shortValue());
        } else {
            return new RuntimeValueNumber(a.intValue() - b.intValue());
        }
    }

    public static RuntimeValueBoolean lessThan(RuntimeValueNumber x, RuntimeValueNumber y) {
        Number a = x.getValue();
        Number b = y.getValue();

        if (a instanceof Double || b instanceof Double) {
            return RuntimeValueBoolean.of(a.doubleValue() < b.doubleValue());
        } else if (a instanceof Float || b instanceof Float) {
            return RuntimeValueBoolean.of(a.floatValue() < b.floatValue());
        } else if (a instanceof Long || b instanceof Long) {
            return RuntimeValueBoolean.of(a.longValue() < b.longValue());
        } else if (a instanceof Byte || b instanceof Byte) {
            return RuntimeValueBoolean.of(a.byteValue() < b.byteValue());
        } else if (a instanceof Short || b instanceof Short) {
            return RuntimeValueBoolean.of(a.shortValue() < b.shortValue());
        } else {
            return RuntimeValueBoolean.of(a.intValue() < b.intValue());
        }
    }

    public static RuntimeValueBoolean numberEquals(RuntimeValueNumber x, RuntimeValueNumber y) {
        // Can be used for IntegerEquals, FloatEquals etc.
        Number a = x.getValue();
        Number b = y.getValue();

        if (a instanceof Double || b instanceof Double) {
            return RuntimeValueBoolean.of(a.doubleValue() == b.doubleValue());
        } else if (a instanceof Float || b instanceof Float) {
            return RuntimeValueBoolean.of(a.floatValue() == b.floatValue());
        } else if (a instanceof Long || b instanceof Long) {
            return RuntimeValueBoolean.of(a.longValue() == b.longValue());
        } else if (a instanceof Byte || b instanceof Byte) {
            return RuntimeValueBoolean.of(a.byteValue() == b.byteValue());
        } else if (a instanceof Short || b instanceof Short) {
            return RuntimeValueBoolean.of(a.shortValue() == b.shortValue());
        } else {
            return RuntimeValueBoolean.of(a.intValue() == b.intValue());
        }
    }

    public static RuntimeValueNumber mul(RuntimeValueNumber x, RuntimeValueNumber y) {
        Number a = x.getValue();
        Number b = y.getValue();

        if (a instanceof Double || b instanceof Double) {
            return new RuntimeValueNumber(a.doubleValue() * b.doubleValue());
        } else if (a instanceof Float || b instanceof Float) {
            return new RuntimeValueNumber(a.floatValue() * b.floatValue());
        } else if (a instanceof Long || b instanceof Long) {
            return new RuntimeValueNumber(a.longValue() * b.longValue());
        } else if (a instanceof Byte || b instanceof Byte) {
            return new RuntimeValueNumber(a.byteValue() * b.byteValue());
        } else if (a instanceof Short || b instanceof Short) {
            return new RuntimeValueNumber(a.shortValue() * b.shortValue());
        } else {
            return new RuntimeValueNumber(a.intValue() * b.intValue());
        }
    }

    // Assuming int type for all of these?
    public static RuntimeValueNumber rightShift(RuntimeValueNumber x, RuntimeValueNumber y) {
        Number a = x.getValue();
        Number b = y.getValue();

        if (a instanceof Long || b instanceof Long) {
            return new RuntimeValueNumber(a.longValue() >> b.longValue());
        } else if (a instanceof Byte || b instanceof Byte) {
            return new RuntimeValueNumber(a.byteValue() >> b.byteValue());
        } else if (a instanceof Short || b instanceof Short) {
            return new RuntimeValueNumber(a.shortValue() >> b.shortValue());
        } else {
            return new RuntimeValueNumber(a.intValue() >> b.intValue());
        }
    }

    public static RuntimeValueNumber signedDiv(RuntimeValueNumber x, RuntimeValueNumber y) {

        Number a = x.getValue();
        Number b = y.getValue();

        if (a instanceof Double || b instanceof Double) {
            return new RuntimeValueNumber(a.doubleValue() / b.doubleValue());
        } else if (a instanceof Float || b instanceof Float) {
            return new RuntimeValueNumber(a.floatValue() / b.floatValue());
        } else if (a instanceof Long || b instanceof Long) {
            return new RuntimeValueNumber(a.longValue() / b.longValue());
        } else if (a instanceof Byte || b instanceof Byte) {
            return new RuntimeValueNumber(a.byteValue() / b.byteValue());
        } else if (a instanceof Short || b instanceof Short) {
            return new RuntimeValueNumber(a.shortValue() / b.shortValue());
        } else {
            return new RuntimeValueNumber(a.intValue() / b.intValue());
        }
    }

    public static RuntimeValueNumber signedRem(RuntimeValueNumber x, RuntimeValueNumber y) {
        Number a = x.getValue();
        Number b = y.getValue();

        if (a instanceof Double || b instanceof Double) {
            return new RuntimeValueNumber(a.doubleValue() % b.doubleValue());
        } else if (a instanceof Float || b instanceof Float) {
            return new RuntimeValueNumber(a.floatValue() % b.floatValue());
        } else if (a instanceof Long || b instanceof Long) {
            return new RuntimeValueNumber(a.longValue() % b.longValue());
        } else if (a instanceof Byte || b instanceof Byte) {
            return new RuntimeValueNumber(a.byteValue() % b.byteValue());
        } else if (a instanceof Short || b instanceof Short) {
            return new RuntimeValueNumber(a.shortValue() % b.shortValue());
        } else {
            return new RuntimeValueNumber(a.intValue() % b.intValue());
        }
    }

    public static RuntimeValueNumber unsignedRightShift(RuntimeValueNumber x, RuntimeValueNumber y) {
        Number a = x.getValue();
        Number b = y.getValue();

        if (a instanceof Long || b instanceof Long) {
            return new RuntimeValueNumber(a.longValue() >>> b.longValue());
        } else if (a instanceof Byte || b instanceof Byte) {
            return new RuntimeValueNumber(a.byteValue() >>> b.byteValue());
        } else if (a instanceof Short || b instanceof Short) {
            return new RuntimeValueNumber(a.shortValue() >>> b.shortValue());
        } else {
            return new RuntimeValueNumber(a.intValue() >>> b.intValue());
        }
    }

    public static RuntimeValueNumber leftShift(RuntimeValueNumber x, RuntimeValueNumber y) {
        Number a = x.getValue();
        Number b = y.getValue();

        if (a instanceof Long || b instanceof Long) {
            return new RuntimeValueNumber(a.longValue() << b.longValue());
        } else if (a instanceof Byte || b instanceof Byte) {
            return new RuntimeValueNumber(a.byteValue() << b.byteValue());
        } else if (a instanceof Short || b instanceof Short) {
            return new RuntimeValueNumber(a.shortValue() << b.shortValue());
        } else {
            return new RuntimeValueNumber(a.intValue() << b.intValue());
        }
    }


}
