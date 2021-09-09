package org.graalvm.compiler.core.runtimetypes;

import org.graalvm.compiler.nodes.RuntimeType;

public class RTNumber extends RuntimeType {

    // todo consider all subclasses of Number:
    // AtomicDouble, AtomicInteger, AtomicLong, BigDecimal, BigFraction, BigInteger, Byte,
    // Decimal64, Double, Float, Fraction, Integer, Long, Short, Striped64, UnsignedInteger,
    // UnsignedLong

    // todo consider using BigDecimal for float and double

    private Number value;

    public RTNumber(Number number) {
        this.value = number;
    }

    public Number getValue() {
        return value;
    }

    public static RTNumber add(RTNumber x, RTNumber y) {
        Number a = x.getValue();
        Number b = y.getValue();

        if (a instanceof Double || b instanceof Double) {
            return new RTNumber(a.doubleValue() + b.doubleValue());
        } else if (a instanceof Float || b instanceof Float) {
            return new RTNumber(a.floatValue() + b.floatValue());
        } else if (a instanceof Long || b instanceof Long) {
            return new RTNumber(a.longValue() + b.longValue());
        } else if (a instanceof Byte || b instanceof Byte) {
            return new RTNumber(a.byteValue() + b.byteValue());
        } else if (a instanceof Short || b instanceof Short) {
            return new RTNumber(a.shortValue() + b.shortValue());
        } else {
            return new RTNumber(a.intValue() + b.intValue());
        }
    }

    public static RTNumber sub(RTNumber x, RTNumber y) {
        Number a = x.getValue();
        Number b = y.getValue();

        if (a instanceof Double || b instanceof Double) {
            return new RTNumber(a.doubleValue() - b.doubleValue());
        } else if (a instanceof Float || b instanceof Float) {
            return new RTNumber(a.floatValue() - b.floatValue());
        } else if (a instanceof Long || b instanceof Long) {
            return new RTNumber(a.longValue() - b.longValue());
        } else if (a instanceof Byte || b instanceof Byte) {
            return new RTNumber(a.byteValue() - b.byteValue());
        } else if (a instanceof Short || b instanceof Short) {
            return new RTNumber(a.shortValue() - b.shortValue());
        } else {
            return new RTNumber(a.intValue() - b.intValue());
        }
    }

    public static RTBoolean lessThan(RTNumber x, RTNumber y) {
        Number a = x.getValue();
        Number b = y.getValue();

        if (a instanceof Double || b instanceof Double) {
            return new RTBoolean(a.doubleValue() < b.doubleValue());
        } else if (a instanceof Float || b instanceof Float) {
            return new RTBoolean(a.floatValue() < b.floatValue());
        } else if (a instanceof Long || b instanceof Long) {
            return new RTBoolean(a.longValue() < b.longValue());
        } else if (a instanceof Byte || b instanceof Byte) {
            return new RTBoolean(a.byteValue() < b.byteValue());
        } else if (a instanceof Short || b instanceof Short) {
            return new RTBoolean(a.shortValue() < b.shortValue());
        } else {
            return new RTBoolean(a.intValue() < b.intValue());
        }
    }

    public static RTBoolean numberEquals(RTNumber x, RTNumber y) {
        // Can be used for IntegerEquals, FloatEquals etc.
        Number a = x.getValue();
        Number b = y.getValue();

        if (a instanceof Double || b instanceof Double) {
            return new RTBoolean(a.doubleValue() == b.doubleValue());
        } else if (a instanceof Float || b instanceof Float) {
            return new RTBoolean(a.floatValue() == b.floatValue());
        } else if (a instanceof Long || b instanceof Long) {
            return new RTBoolean(a.longValue() == b.longValue());
        } else if (a instanceof Byte || b instanceof Byte) {
            return new RTBoolean(a.byteValue() == b.byteValue());
        } else if (a instanceof Short || b instanceof Short) {
            return new RTBoolean(a.shortValue() == b.shortValue());
        } else {
            return new RTBoolean(a.intValue() == b.intValue());
        }
    }

    public static RTNumber mul(RTNumber x, RTNumber y) {
        Number a = x.getValue();
        Number b = y.getValue();

        if (a instanceof Double || b instanceof Double) {
            return new RTNumber(a.doubleValue() * b.doubleValue());
        } else if (a instanceof Float || b instanceof Float) {
            return new RTNumber(a.floatValue() * b.floatValue());
        } else if (a instanceof Long || b instanceof Long) {
            return new RTNumber(a.longValue() * b.longValue());
        } else if (a instanceof Byte || b instanceof Byte) {
            return new RTNumber(a.byteValue() * b.byteValue());
        } else if (a instanceof Short || b instanceof Short) {
            return new RTNumber(a.shortValue() * b.shortValue());
        } else {
            return new RTNumber(a.intValue() * b.intValue());
        }
    }

    // Assuming int type for all of these?
    public static RTNumber rightShift(RTNumber x, RTNumber y) {
        Number a = x.getValue();
        Number b = y.getValue();

        if (a instanceof Long || b instanceof Long) {
            return new RTNumber(a.longValue() >> b.longValue());
        } else if (a instanceof Byte || b instanceof Byte) {
            return new RTNumber(a.byteValue() >> b.byteValue());
        } else if (a instanceof Short || b instanceof Short) {
            return new RTNumber(a.shortValue() >> b.shortValue());
        } else {
            return new RTNumber(a.intValue() >> b.intValue());
        }
    }

    public static RTNumber signedDiv(RTNumber x, RTNumber y) {

        Number a = x.getValue();
        Number b = y.getValue();

        if (a instanceof Double || b instanceof Double) {
            return new RTNumber(a.doubleValue() / b.doubleValue());
        } else if (a instanceof Float || b instanceof Float) {
            return new RTNumber(a.floatValue() / b.floatValue());
        } else if (a instanceof Long || b instanceof Long) {
            return new RTNumber(a.longValue() / b.longValue());
        } else if (a instanceof Byte || b instanceof Byte) {
            return new RTNumber(a.byteValue() / b.byteValue());
        } else if (a instanceof Short || b instanceof Short) {
            return new RTNumber(a.shortValue() / b.shortValue());
        } else {
            return new RTNumber(a.intValue() / b.intValue());
        }
    }

    public static RuntimeType signedRem(RTNumber x, RTNumber y) {
        Number a = x.getValue();
        Number b = y.getValue();

        if (a instanceof Double || b instanceof Double) {
            return new RTNumber(a.doubleValue() % b.doubleValue());
        } else if (a instanceof Float || b instanceof Float) {
            return new RTNumber(a.floatValue() % b.floatValue());
        } else if (a instanceof Long || b instanceof Long) {
            return new RTNumber(a.longValue() % b.longValue());
        } else if (a instanceof Byte || b instanceof Byte) {
            return new RTNumber(a.byteValue() % b.byteValue());
        } else if (a instanceof Short || b instanceof Short) {
            return new RTNumber(a.shortValue() % b.shortValue());
        } else {
            return new RTNumber(a.intValue() % b.intValue());
        }
    }

    public static RTNumber unsignedRightShift(RTNumber x, RTNumber y) {
        Number a = x.getValue();
        Number b = y.getValue();

        if (a instanceof Long || b instanceof Long) {
            return new RTNumber(a.longValue() >>> b.longValue());
        } else if (a instanceof Byte || b instanceof Byte) {
            return new RTNumber(a.byteValue() >>> b.byteValue());
        } else if (a instanceof Short || b instanceof Short) {
            return new RTNumber(a.shortValue() >>> b.shortValue());
        } else {
            return new RTNumber(a.intValue() >>> b.intValue());
        }
    }

    public static RTNumber leftShift(RTNumber x, RTNumber y) {
        Number a = x.getValue();
        Number b = y.getValue();

        if (a instanceof Long || b instanceof Long) {
            return new RTNumber(a.longValue() << b.longValue());
        } else if (a instanceof Byte || b instanceof Byte) {
            return new RTNumber(a.byteValue() << b.byteValue());
        } else if (a instanceof Short || b instanceof Short) {
            return new RTNumber(a.shortValue() << b.shortValue());
        } else {
            return new RTNumber(a.intValue() << b.intValue());
        }
    }

    // todo check java behaviour
    public RuntimeType createRuntimeBoolean() {
        if (value instanceof Double) {
            return new RTBoolean(value.doubleValue() != 0);
        } else if (value instanceof Float) {
            return new RTBoolean(value.floatValue() != 0);
        } else if (value instanceof Long) {
            return new RTBoolean(value.longValue() != 0);
        } else {
            return new RTBoolean(value.intValue() != 0);
        }
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
        return createRuntimeBoolean().getBoolean();
    }

}
