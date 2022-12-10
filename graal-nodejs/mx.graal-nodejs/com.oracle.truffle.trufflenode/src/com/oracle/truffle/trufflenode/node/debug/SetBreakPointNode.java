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
package com.oracle.truffle.trufflenode.node.debug;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.runtime.JSArguments;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.JavaScriptRootNode;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.builtins.JSFunctionObject;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.trufflenode.GraalJSAccess;

public class SetBreakPointNode extends JavaScriptRootNode {
    public static final TruffleString NAME = Strings.constant("setBreakPoint");

    @Override
    public Object execute(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        int numArgs = JSArguments.getUserArgumentCount(args);
        Object arg0 = Undefined.instance;
        if (numArgs >= 1) {
            arg0 = JSArguments.getUserArgument(args, 0);
        }
        if (JSFunction.isJSFunction(arg0)) {
            CallTarget callTarget = JSFunction.getFunctionData((JSFunctionObject) arg0).getCallTarget();
            if (callTarget instanceof RootCallTarget) {
                return addBreakPoint((RootCallTarget) callTarget, args);
            }
        }
        unsupported();
        return 0;
    }

    @CompilerDirectives.TruffleBoundary
    private int addBreakPoint(RootCallTarget callTarget, Object[] args) {
        int numArgs = JSArguments.getUserArgumentCount(args);
        Node rootNode = callTarget.getRootNode();
        SourceSection sourceSection = rootNode.getSourceSection();
        Source source = sourceSection.getSource();
        int lineNo = sourceSection.getStartLine();
        int columnNo = sourceSection.getStartColumn();
        int userLine = 0;
        if (numArgs >= 2) {
            Object arg1 = JSArguments.getUserArgument(args, 1);
            if (arg1 instanceof Number) {
                userLine = ((Number) arg1).intValue();
                lineNo += userLine;
            }
        }
        if (userLine > 0) {
            columnNo = 1; // ignore the start column of root node's source section
        }
        if (numArgs >= 3) {
            Object arg2 = JSArguments.getUserArgument(args, 2);
            if (arg2 instanceof Number) {
                columnNo += ((Number) arg2).intValue();
            }
        }
        // JSArguments.getUserArgument(args, 3) is condition
        // We do not support conditional breakpoints here (yet?)
        boolean oneShot = false;
        if (numArgs >= 5) {
            Object arg4 = JSArguments.getUserArgument(args, 4);
            oneShot = JSRuntime.toBoolean(arg4);
        }

        // Heuristics used by the debugger to resolve the statement
        // for the given breakpoint considers all call targets =>
        // it sometimes places the breakpoint into a nested function (incorrectly).
        // This code attempts to help the debugger to place the breakpoint
        // correctly by providing the exact offset of the desired statement
        // (within the specified callTarget).
        lineNo = Math.max(1, Math.min(lineNo, source.getLineCount()));
        columnNo = Math.max(1, Math.min(columnNo, source.getLineLength(lineNo)));
        int offset = source.getLineStartOffset(lineNo) + columnNo - 1;
        BreakPointOffsetFinder visitor = new BreakPointOffsetFinder(offset);
        rootNode.accept(visitor);
        int bestOffset = visitor.getBestOffset();
        lineNo = source.getLineNumber(bestOffset);
        columnNo = bestOffset - source.getLineStartOffset(lineNo) + 1;

        addBreakPoint(source, lineNo, columnNo, oneShot);
        return 0;
    }

    static class BreakPointOffsetFinder implements NodeVisitor {
        private final int expectedOffset;
        private int bestOffset = Integer.MAX_VALUE;

        BreakPointOffsetFinder(int expectedOffset) {
            this.expectedOffset = expectedOffset;
        }

        int getBestOffset() {
            return (bestOffset == Integer.MAX_VALUE) ? expectedOffset : bestOffset;
        }

        @Override
        public boolean visit(Node node) {
            if (node instanceof InstrumentableNode && ((InstrumentableNode) node).hasTag(StandardTags.StatementTag.class)) {
                SourceSection section = node.getSourceSection();
                if (section != null && section.isAvailable()) {
                    int offset = section.getCharIndex();
                    if (expectedOffset <= offset && offset < bestOffset) {
                        bestOffset = offset;
                    }
                }
            }
            return true;
        }

    }

    @CompilerDirectives.TruffleBoundary
    private void addBreakPoint(Source source, int lineNo, int columnNo, boolean oneShot) {
        Breakpoint.Builder builder = Breakpoint.newBuilder(source).lineIs(lineNo).columnIs(columnNo);
        if (oneShot) {
            builder.oneShot();
        }
        Breakpoint breakpoint = builder.build();
        GraalJSAccess graalJSAccess = GraalJSAccess.get(this);
        Debugger debugger = graalJSAccess.lookupInstrument("debugger", Debugger.class);
        debugger.install(breakpoint);
    }

    @CompilerDirectives.TruffleBoundary
    private static void unsupported() {
        System.err.println("Unsupported usage of Debug.setBreakpoint!");
    }

    @CompilerDirectives.TruffleBoundary
    private static boolean startsWith(SourceSection sourceSection, String prefix) {
        CharSequence characters = sourceSection.getCharacters();
        int n = prefix.length();
        if (n > characters.length()) {
            return false;
        }
        return characters.subSequence(0, n).toString().equals(prefix);
    }

}
