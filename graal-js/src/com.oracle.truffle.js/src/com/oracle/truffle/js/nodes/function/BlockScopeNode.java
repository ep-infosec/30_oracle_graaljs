/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.nodes.function;

import java.util.Set;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.js.nodes.FrameDescriptorProvider;
import com.oracle.truffle.js.nodes.JSFrameSlot;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.control.ResumableNode;
import com.oracle.truffle.js.nodes.control.TryCatchNode;
import com.oracle.truffle.js.nodes.control.YieldException;
import com.oracle.truffle.js.nodes.instrumentation.DeclareTagProvider;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.DeclareTag;
import com.oracle.truffle.js.runtime.JSArguments;
import com.oracle.truffle.js.runtime.JSFrameUtil;
import com.oracle.truffle.js.runtime.objects.Null;
import com.oracle.truffle.js.runtime.objects.Undefined;

public abstract class BlockScopeNode extends NamedEvaluationTargetNode implements RepeatingNode {
    @Child protected JavaScriptNode block;

    protected BlockScopeNode(JavaScriptNode block) {
        this.block = block;
    }

    public static BlockScopeNode create(JavaScriptNode block, JSFrameSlot blockScopeSlot, FrameDescriptor frameDescriptor, JSFrameSlot parentSlot,
                    boolean functionBlock, boolean captureFunctionFrame, boolean generatorFunctionBlock, boolean hasParentBlock, int start, int end) {
        return new FrameBlockScopeNode(block, blockScopeSlot.getIndex(), frameDescriptor, parentSlot.getIndex(),
                        functionBlock, captureFunctionFrame, generatorFunctionBlock, hasParentBlock, start, end);
    }

    public static BlockScopeNode createVirtual(JavaScriptNode block, int frameStart, int frameEnd) {
        return new VirtualBlockScopeNode(block, frameStart, frameEnd);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            return block.execute(appendScopeFrame(frame));
        } finally {
            exitScope(frame);
        }
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        try {
            block.executeVoid(appendScopeFrame(frame));
        } finally {
            exitScope(frame);
        }
    }

    public abstract VirtualFrame appendScopeFrame(VirtualFrame frame);

    public final void exitScope(VirtualFrame frame) {
        exitScope(frame, false);
    }

    public abstract void exitScope(VirtualFrame frame, boolean yield);

    public abstract Object getBlockScope(VirtualFrame frame);

    public abstract void setBlockScope(VirtualFrame frame, Object state);

    @Override
    public boolean executeRepeating(VirtualFrame frame) {
        try {
            return ((RepeatingNode) block).executeRepeating(appendScopeFrame(frame));
        } finally {
            exitScope(frame);
        }
    }

    @Override
    public Object executeWithName(VirtualFrame frame, Object name) {
        try {
            return ((NamedEvaluationTargetNode) block).executeWithName(appendScopeFrame(frame), name);
        } finally {
            exitScope(frame);
        }
    }

    public JavaScriptNode getBlock() {
        return block;
    }

    public abstract boolean isFunctionBlock();

    @Override
    public boolean isResultAlwaysOfType(Class<?> clazz) {
        return block.isResultAlwaysOfType(clazz);
    }

    public int getFrameStart() {
        return -1;
    }

    public int getFrameEnd() {
        return -1;
    }

    public static class FrameBlockScopeNode extends BlockScopeNode implements ResumableNode.WithObjectState, FrameDescriptorProvider {
        protected final int blockScopeSlot;
        protected final int parentSlot;
        protected final FrameDescriptor frameDescriptor;
        /** If true, this is the function-level block scope. */
        protected final boolean functionBlock;
        /** If true, put the virtual function frame in the parent scope slot. */
        protected final boolean captureFunctionFrame;
        /** If true, this is a block scope in the generator function prologue. */
        protected final boolean generatorFunctionBlock;
        protected final boolean hasParentBlock;
        /** Virtual frame slots start index (inclusive). */
        protected final int start;
        /** Virtual frame slots end index (exclusive). */
        protected final int end;

        protected FrameBlockScopeNode(JavaScriptNode block, int blockScopeSlot, FrameDescriptor frameDescriptor, int parentSlot,
                        boolean functionBlock, boolean captureFunctionFrame, boolean generatorFunctionBlock, boolean hasParentBlock, int start, int end) {
            super(block);
            this.blockScopeSlot = blockScopeSlot;
            this.parentSlot = parentSlot;
            this.functionBlock = functionBlock;
            this.captureFunctionFrame = captureFunctionFrame;
            this.generatorFunctionBlock = generatorFunctionBlock;
            this.hasParentBlock = hasParentBlock;
            this.start = start;
            this.end = end;
            this.frameDescriptor = frameDescriptor;
        }

        @Override
        public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
            if (materializedTags.contains(DeclareTag.class) && !DeclareTagProvider.isMaterializedFrameProvider(this)) {
                JavaScriptNode materialized = DeclareTagProvider.createMaterializedBlockNode(this, cloneUninitialized(block, materializedTags),
                                blockScopeSlot, frameDescriptor, parentSlot, functionBlock, captureFunctionFrame, generatorFunctionBlock, hasParentBlock, start, end);
                transferSourceSectionAndTags(this, materialized);
                return materialized;
            } else {
                return this;
            }
        }

        @Override
        public VirtualFrame appendScopeFrame(VirtualFrame frame) {
            Object parentScopeFrame = frame.getObject(blockScopeSlot);
            if (captureFunctionFrame) {
                assert parentScopeFrame == Undefined.instance;
                parentScopeFrame = frame.materialize();
            }
            Object[] arguments;
            if (hasParentBlock) {
                arguments = JSFrameUtil.castMaterializedFrame(parentScopeFrame).getArguments();
            } else {
                assert parentScopeFrame == Undefined.instance || captureFunctionFrame;
                arguments = JSArguments.createZeroArg(Undefined.instance, JSFrameUtil.getFunctionObjectNoCast(frame));
            }
            MaterializedFrame scopeFrame = Truffle.getRuntime().createVirtualFrame(arguments, frameDescriptor).materialize();
            scopeFrame.setObject(parentSlot, parentScopeFrame);
            frame.setObject(blockScopeSlot, scopeFrame);
            return frame;
        }

        @Override
        public void exitScope(VirtualFrame frame, boolean yield) {
            MaterializedFrame blockScopeFrame = JSFrameUtil.castMaterializedFrame(frame.getObject(blockScopeSlot));
            assert blockScopeFrame.getFrameDescriptor() == frameDescriptor : blockScopeFrame.getFrameDescriptor();
            if (generatorFunctionBlock) {
                // Keep block scope entered when exiting the generator function prologue.
                // When the generator starts, it expects the block scope to still be entered.
                return;
            }
            Object parentScopeFrame = blockScopeFrame.getObject(parentSlot);
            if (captureFunctionFrame) {
                assert ((Frame) parentScopeFrame).getFrameDescriptor() == frame.getFrameDescriptor();
                // Avoid self reference
                parentScopeFrame = Undefined.instance;
            }
            frame.setObject(blockScopeSlot, parentScopeFrame);
            if (!yield && start < end) {
                clearVirtualSlots(frame);
            }
        }

        @ExplodeLoop
        private void clearVirtualSlots(VirtualFrame frame) {
            for (int i = start; i < end; i++) {
                frame.clear(i);
            }
        }

        @Override
        public FrameDescriptor getFrameDescriptor() {
            return frameDescriptor;
        }

        @Override
        public Object resume(VirtualFrame frame, int stateSlot) {
            // Q: Why do we exit the scope when we yield even though we resume back into it anyway?
            // A: This is in order to allow (side-effect-free) uses of outer block frame slots
            // during resumption before we reach this block scope again.
            // A simple example would be an assignment that checks the frame slot type before it
            // executes/resumes the right hand side, e.g.:
            // `let C = <scope> class C { [await](){} } </scope>;`
            Object state = getStateAndReset(frame, stateSlot);
            if (state == Undefined.instance) {
                appendScopeFrame(frame);
            } else {
                setBlockScope(frame, state);
            }
            boolean yield = false;
            try {
                return block.execute(frame);
            } catch (YieldException e) {
                yield = true;
                setState(frame, stateSlot, getBlockScope(frame));
                throw e;
            } finally {
                exitScope(frame, yield);
            }
        }

        @Override
        public Object getBlockScope(VirtualFrame frame) {
            return frame.getObject(blockScopeSlot);
        }

        @Override
        public void setBlockScope(VirtualFrame frame, Object state) {
            assert state instanceof MaterializedFrame;
            frame.setObject(blockScopeSlot, state);
        }

        @Override
        public boolean isFunctionBlock() {
            return functionBlock;
        }

        @Override
        public int getFrameStart() {
            return start;
        }

        @Override
        public int getFrameEnd() {
            return end;
        }

        @Override
        protected JavaScriptNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new FrameBlockScopeNode(cloneUninitialized(block, materializedTags),
                            blockScopeSlot, frameDescriptor, parentSlot, functionBlock, captureFunctionFrame, generatorFunctionBlock, hasParentBlock, start, end);
        }
    }

    /**
     * Represents a local block scope that has been merged into the function's virtual frame. Serves
     * as metadata to reconstruct the original block structure for debugging purposes and clears the
     * block's frame slots on leave to limit value lifetimes. Not needed for correct execution.
     */
    public static class VirtualBlockScopeNode extends BlockScopeNode implements ResumableNode {
        protected final int start;
        protected final int end;

        /** A value other than undefined to signal that block node is entered. */
        private static final Object SCOPE_PLACEHOLDER = Null.instance;

        protected VirtualBlockScopeNode(JavaScriptNode block, int start, int end) {
            super(block);
            this.start = start;
            this.end = end;
        }

        @Override
        public VirtualFrame appendScopeFrame(VirtualFrame frame) {
            return frame;
        }

        @ExplodeLoop
        @Override
        public void exitScope(VirtualFrame frame, boolean yield) {
            // Clear frame slots when exiting the scope.
            // Note: we must not clear the slots on yield/await.
            if (!yield) {
                for (int i = start; i < end; i++) {
                    frame.clear(i);
                }
            }
        }

        @Override
        public Object resume(VirtualFrame frame, int stateSlot) {
            boolean yield = false;
            try {
                return block.execute(appendScopeFrame(frame));
            } catch (YieldException e) {
                yield = true;
                throw e;
            } finally {
                exitScope(frame, yield);
            }
        }

        /**
         * @see TryCatchNode#resume
         */
        @Override
        public Object getBlockScope(VirtualFrame frame) {
            return SCOPE_PLACEHOLDER;
        }

        @Override
        public void setBlockScope(VirtualFrame frame, Object state) {
            assert state == SCOPE_PLACEHOLDER;
        }

        @Override
        public boolean isFunctionBlock() {
            return false;
        }

        @Override
        public int getFrameStart() {
            return start;
        }

        @Override
        public int getFrameEnd() {
            return end;
        }

        @Override
        protected JavaScriptNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new VirtualBlockScopeNode(cloneUninitialized(block, materializedTags), start, end);
        }
    }
}
