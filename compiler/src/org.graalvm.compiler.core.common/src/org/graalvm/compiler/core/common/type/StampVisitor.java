package org.graalvm.compiler.core.common.type;

public interface StampVisitor<T> {
    T visit(FloatStamp stamp);

    T visit(IllegalStamp stamp);

    T visit(IntegerStamp stamp);

    T visit(ObjectStamp stamp);

    T visit(RawPointerStamp stamp);

    T visit(VoidStamp stamp);

    // we only add stamp subclasses that are in this module
    // so this is the closest superclass for the HotSpot stamps
    // See HotSpotStampVisitor for more precise visit methods.
    T visit(AbstractPointerStamp stamp);
}
