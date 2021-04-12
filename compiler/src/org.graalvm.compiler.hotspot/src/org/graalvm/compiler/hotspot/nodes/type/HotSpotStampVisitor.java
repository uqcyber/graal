package org.graalvm.compiler.hotspot.nodes.type;


import org.graalvm.compiler.core.common.type.StampVisitor;

/**
 * Stamp visitors that can handle the HotSpot specific stamps.
 *
 * These more-specific methods will be called in preference to visit(AbstractPointerStamp).
 * @param <T>
 */
public interface HotSpotStampVisitor<T> extends StampVisitor<T> {
    T visit(HotSpotNarrowOopStamp stamp);
    T visit(KlassPointerStamp stamp);
    T visit(MethodCountersPointerStamp stamp);
    T visit(MethodPointerStamp stamp);
}
