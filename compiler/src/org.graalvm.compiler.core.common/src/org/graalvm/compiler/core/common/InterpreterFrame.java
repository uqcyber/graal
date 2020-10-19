package org.graalvm.compiler.core.common;

// Interpreter frame object based on the methods employed in the FrameState class
public class InterpreterFrame {
    InterpreterFrame outerFrame;
    int stackSize;

    public InterpreterFrame(int stackSize, InterpreterFrame outerFrame){
        //todo consider virtual object mappings, node input list, exceptions
        assert stackSize >= 0;
        this.stackSize = stackSize;
        this.outerFrame = outerFrame;
        assert outerFrame == null || outerFrame.stackSize >= 0;
    }

    public InterpreterFrame(int stackSize) {
        assert stackSize >= 0;
        this.stackSize = stackSize;
        this.outerFrame = null;
    }
}
