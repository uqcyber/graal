package org.graalvm.compiler.core.common.type;

import org.graalvm.compiler.hotspot.nodes.type.HotSpotNarrowOopStamp;
import org.graalvm.compiler.hotspot.nodes.type.KlassPointerStamp;
import org.graalvm.compiler.hotspot.nodes.type.MethodCountersPointerStamp;
import org.graalvm.compiler.hotspot.nodes.type.MethodPointerStamp;

public interface StampVisitor<T> {
    T visit(FloatStamp stamp);
    T visit(IllegalStamp stamp);
    T visit(IntegerStamp stamp);
    T visit(ObjectStamp stamp);
    T visit(RawPointerStamp stamp);
    T visit(VoidStamp stamp);

    T visit(HotSpotNarrowOopStamp stamp);
    T visit(KlassPointerStamp stamp);
    T visit(MethodCountersPointerStamp stamp);
    T visit(MethodPointerStamp stamp);
}
