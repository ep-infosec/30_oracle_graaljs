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

import com.oracle.truffle.js.nodes.instrumentation.JSTags.FunctionCallTag;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.ObjectAllocationTag;

public class YieldTest extends FineGrainedAccessTest {

    @Test
    public void yieldAsArgumentNew() {
        evalWithTag("class Noop {" +
                        "  constructor(obj) {" +
                        "    this.obj = obj;" +
                        "  }" +
                        "};" +
                        "function* boom() {" +
                        "  return new Noop(yield);" +
                        "};" +
                        "a = boom();" +
                        "a.next();" +
                        "a.next(42);", ObjectAllocationTag.class);

        enter(ObjectAllocationTag.class, (e, call) -> {
            call.input(assertJSFunctionInputWithName("Noop"));
            call.input(42);
        }).exit();
    }

    @Test
    public void yieldAsArgumentCall() {
        evalWithTag("function Noop(obj) {" +
                        "};" +
                        "function* boom() {" +
                        "  return Noop(yield);" +
                        "};" +
                        "a = boom();" +
                        "a.next();" +
                        "a.next(42);", FunctionCallTag.class);

        enter(FunctionCallTag.class, (e, call) -> {
            call.input(assertUndefinedInput);
            call.input(assertJSFunctionInputWithName("boom"));
        }).exit();
        enter(FunctionCallTag.class, (e, call) -> {
            call.input(assertJSObjectInput);
            call.input(assertJSFunctionInputWithName("next"));
        }).exit();

        enter(FunctionCallTag.class, (e, call) -> {
            call.input(assertJSObjectInput);
            call.input(assertJSFunctionInputWithName("next"));
            call.input(42);

            enter(FunctionCallTag.class, (e1, call1) -> {
                call.input(assertUndefinedInput);
                call.input(assertJSFunctionInputWithName("Noop"));
                call.input(42);

            }).exit();
        }).exit();
    }

    @Test
    public void yieldAsArgumentInvoke() {
        evalWithTag("const Noop = { fun : function(obj) {}};" +
                        "function* boom() {" +
                        "  return Noop.fun(yield);" +
                        "};" +
                        "a = boom();" +
                        "a.next();" +
                        "a.next(42);", FunctionCallTag.class);

        enter(FunctionCallTag.class, (e, call) -> {
            call.input(assertUndefinedInput);
            call.input(assertJSFunctionInputWithName("boom"));
        }).exit();
        enter(FunctionCallTag.class, (e, call) -> {
            call.input(assertJSObjectInput);
            call.input(assertJSFunctionInputWithName("next"));
        }).exit();

        enter(FunctionCallTag.class, (e, call) -> {
            call.input(assertJSObjectInput);
            call.input(assertJSFunctionInputWithName("next"));
            call.input(42);

            enter(FunctionCallTag.class, (e1, call1) -> {
                call.input(assertJSObjectInput);
                call.input(assertJSFunctionInputWithName("fun"));
                call.input(42);
            }).exit();
        }).exit();
    }

}
