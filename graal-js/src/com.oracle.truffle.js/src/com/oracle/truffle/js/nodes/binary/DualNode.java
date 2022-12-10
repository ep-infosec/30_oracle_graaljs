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
package com.oracle.truffle.js.nodes.binary;

import java.util.Set;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.js.nodes.JSNodeUtil;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.control.AbstractBlockNode;
import com.oracle.truffle.js.nodes.control.ExprBlockNode;
import com.oracle.truffle.js.nodes.control.ResumableNode;
import com.oracle.truffle.js.nodes.control.SequenceNode;
import com.oracle.truffle.js.nodes.control.VoidBlockNode;
import com.oracle.truffle.js.nodes.control.YieldException;

@NodeInfo(cost = NodeCost.NONE)
public class DualNode extends JavaScriptNode implements SequenceNode, ResumableNode.WithIntState {

    @Child private JavaScriptNode left;
    @Child private JavaScriptNode right;

    public DualNode(JavaScriptNode left, JavaScriptNode right) {
        this.left = left;
        this.right = right;
    }

    public static JavaScriptNode create(JavaScriptNode left, JavaScriptNode right) {
        if (isFlatteningCandidate(left) || isFlatteningCandidate(right)) {
            final int len = getLen(left) + getLen(right);
            if (len > 2) {
                JavaScriptNode[] arr = new JavaScriptNode[len];
                int pos = 0;
                pos = flatten(arr, pos, left);
                pos = flatten(arr, pos, right);
                assert pos == len;
                return right instanceof VoidBlockNode ? VoidBlockNode.createVoidBlock(arr) : ExprBlockNode.createExprBlock(arr);
            }
        }
        return new DualNode(left, right);
    }

    private static boolean isFlatteningCandidate(JavaScriptNode left) {
        return left instanceof DualNode || left instanceof AbstractBlockNode;
    }

    private static int flatten(JavaScriptNode[] arr, int pos, JavaScriptNode node) {
        if (node instanceof DualNode && !JSNodeUtil.hasImportantTag(node)) {
            DualNode dual = (DualNode) node;
            arr[pos] = dual.left;
            arr[pos + 1] = dual.right;
            return pos + 2;
        } else if (node instanceof AbstractBlockNode && !JSNodeUtil.hasImportantTag(node)) {
            AbstractBlockNode block = (AbstractBlockNode) node;
            int len = block.getStatements().length;
            System.arraycopy(block.getStatements(), 0, arr, pos, len);
            return pos + len;
        } else {
            arr[pos] = node;
            return pos + 1;
        }
    }

    private static int getLen(JavaScriptNode node) {
        if (node instanceof DualNode && !JSNodeUtil.hasImportantTag(node)) {
            return 2;
        } else if (node instanceof AbstractBlockNode && !JSNodeUtil.hasImportantTag(node)) {
            return ((AbstractBlockNode) node).getStatements().length;
        } else {
            return 1;
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        left.executeVoid(frame);
        return right.execute(frame);
    }

    @Override
    public int executeInt(VirtualFrame frame) throws UnexpectedResultException {
        left.executeVoid(frame);
        return right.executeInt(frame);
    }

    @Override
    public double executeDouble(VirtualFrame frame) throws UnexpectedResultException {
        left.executeVoid(frame);
        return right.executeDouble(frame);
    }

    @Override
    public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
        left.executeVoid(frame);
        return right.executeBoolean(frame);
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        left.executeVoid(frame);
        right.executeVoid(frame);
    }

    @Override
    public JavaScriptNode[] getStatements() {
        return new JavaScriptNode[]{left, right};
    }

    @Override
    public Object resume(VirtualFrame frame, int stateSlot) {
        int state = getStateAsIntAndReset(frame, stateSlot);
        if (state == 0) {
            left.executeVoid(frame);
            try {
                return right.execute(frame);
            } catch (YieldException e) {
                setStateAsInt(frame, stateSlot, 1);
                throw e;
            }
        } else {
            assert state == 1;
            try {
                return right.execute(frame);
            } catch (YieldException e) {
                setStateAsInt(frame, stateSlot, 1);
                throw e;
            }
        }
    }

    @Override
    public boolean isResultAlwaysOfType(Class<?> clazz) {
        return right.isResultAlwaysOfType(clazz);
    }

    @Override
    protected JavaScriptNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
        return new DualNode(cloneUninitialized(left, materializedTags), cloneUninitialized(right, materializedTags));
    }

    public JavaScriptNode getLeft() {
        return left;
    }

    public JavaScriptNode getRight() {
        return right;
    }
}
