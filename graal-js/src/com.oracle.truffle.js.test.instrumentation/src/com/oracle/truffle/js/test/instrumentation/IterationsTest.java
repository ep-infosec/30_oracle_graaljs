/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.test.instrumentation;

import org.junit.Test;

import com.oracle.truffle.js.nodes.instrumentation.JSTags.ControlFlowBlockTag;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.ControlFlowBranchTag;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.ControlFlowRootTag;

public class IterationsTest extends FineGrainedAccessTest {

    @Test
    public void basicFor() {
        String src = "for (var a=0; a<3; a++) { 42;};";

        evalWithTags(src, new Class[]{
                        ControlFlowRootTag.class,
                        ControlFlowBranchTag.class,
                        ControlFlowBlockTag.class
        }, new Class[]{/* no input events */});

        enter(ControlFlowRootTag.class, (e) -> {
            assertAttribute(e, TYPE, ControlFlowRootTag.Type.ForIteration.name());
            for (int a = 0; a < 3; a++) {
                enter(ControlFlowBranchTag.class).exit(assertReturnValue(true));
                enter(ControlFlowBlockTag.class).exit();
            }
            enter(ControlFlowBranchTag.class).exit(assertReturnValue(false));
        }).exit();
    }

    @Test
    public void basicWhileDo() {
        String src = "var a=0; while(a<3) { a++; };";

        evalWithTags(src, new Class[]{
                        ControlFlowRootTag.class,
                        ControlFlowBranchTag.class,
                        ControlFlowBlockTag.class
        }, new Class[]{/* no input events */});

        enter(ControlFlowRootTag.class, (e) -> {
            assertAttribute(e, TYPE, ControlFlowRootTag.Type.WhileIteration.name());
            for (int a = 0; a < 3; a++) {
                enter(ControlFlowBranchTag.class).exit(assertReturnValue(true));
                enter(ControlFlowBlockTag.class).exit();
            }
            enter(ControlFlowBranchTag.class).exit(assertReturnValue(false));
        }).exit();
    }

    @Test
    public void basicDoWhile() {
        String src = "var a=0; do { a++; } while(a<3);";

        evalWithTags(src, new Class[]{
                        ControlFlowRootTag.class,
                        ControlFlowBranchTag.class,
                        ControlFlowBlockTag.class
        }, new Class[]{/* no input events */});

        enter(ControlFlowRootTag.class, (e) -> {
            assertAttribute(e, TYPE, ControlFlowRootTag.Type.DoWhileIteration.name());
            for (int a = 0; a < 2; a++) {
                enter(ControlFlowBlockTag.class).exit();
                enter(ControlFlowBranchTag.class).exit(assertReturnValue(true));
            }
            enter(ControlFlowBlockTag.class).exit();
            enter(ControlFlowBranchTag.class).exit(assertReturnValue(false));
        }).exit();
    }

    @Test
    public void emptyForLet() {
        String src = "for (let i = 0; i < 3; i++) {};";

        evalWithTags(src, new Class[]{
                        ControlFlowRootTag.class,
                        ControlFlowBranchTag.class,
                        ControlFlowBlockTag.class
        }, new Class[]{/* no input events */});

        enter(ControlFlowRootTag.class, (e) -> {
            assertAttribute(e, TYPE, ControlFlowRootTag.Type.ForIteration.name());
            for (int a = 0; a < 3; a++) {
                enter(ControlFlowBranchTag.class).exit(assertReturnValue(true));
                enter(ControlFlowBlockTag.class).exit();
            }
            enter(ControlFlowBranchTag.class).exit(assertReturnValue(false));
        }).exit();
    }

    @Test
    public void forLetWithPerIterationScope() {
        String src = "for (let i = 0; i < 3; i++) { function dummy(){return i;} };";

        evalWithTags(src, new Class[]{
                        ControlFlowRootTag.class,
                        ControlFlowBranchTag.class,
                        ControlFlowBlockTag.class
        }, new Class[]{/* no input events */});

        enter(ControlFlowRootTag.class, (e) -> {
            assertAttribute(e, TYPE, ControlFlowRootTag.Type.ForIteration.name());
            for (int a = 0; a < 3; a++) {
                enter(ControlFlowBranchTag.class).exit(assertReturnValue(true));
                enter(ControlFlowBlockTag.class).exit();
            }
            enter(ControlFlowBranchTag.class).exit(assertReturnValue(false));
        }).exit();
    }

    @Test
    public void emptyForOf() {
        testForInForOf("var obj = ['a', 'b', 'c']; for (var i of obj) {};", ControlFlowRootTag.Type.ForOfIteration.name());
    }

    @Test
    public void emptyForIn() {
        testForInForOf("var obj = ['a', 'b', 'c']; for (var i in obj) {};", ControlFlowRootTag.Type.ForInIteration.name());
    }

    private void testForInForOf(String src, String expectedName) {
        evalWithTags(src, new Class[]{
                        ControlFlowRootTag.class,
                        ControlFlowBranchTag.class,
                        ControlFlowBlockTag.class
        }, new Class[]{/* no input events */});

        enter(ControlFlowRootTag.class, (e) -> {
            assertAttribute(e, TYPE, expectedName);
            for (int a = 0; a < 3; a++) {
                enter(ControlFlowBlockTag.class).exit();
            }
        }).exit();
    }

}
