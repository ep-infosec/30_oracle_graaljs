/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.test.builtins;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.runtime.JSContextOptions;
import com.oracle.truffle.js.test.JSTest;
import com.oracle.truffle.js.test.interop.AsyncInteropTest;

public class AsyncIteratorHelperPrototypeBuiltinsTest {
    @Test
    public void testPrototype() {
        Context.Builder builder = JSTest.newContextBuilder();
        builder.option(JSContextOptions.ITERATOR_HELPERS_NAME, "true");
        try (Context context = builder.build()) {
            Value result = context.eval(JavaScriptLanguage.ID, "Object.getPrototypeOf(Object.getPrototypeOf(AsyncIterator.from([1]).drop(0))) === AsyncIterator.prototype");
            Assert.assertTrue(result.asBoolean());

            result = context.eval(JavaScriptLanguage.ID, "typeof Object.getPrototypeOf(AsyncIterator.from([1]).drop(0))");
            Assert.assertEquals("object", result.asString());
        }
    }

    @Test
    public void testNext() {
        AsyncInteropTest.TestOutput out = new AsyncInteropTest.TestOutput();
        Context.Builder builder = JSTest.newContextBuilder();
        builder.option(JSContextOptions.ITERATOR_HELPERS_NAME, "true");
        builder.out(out);
        try (Context context = builder.build()) {
            context.eval(JavaScriptLanguage.ID, "(async function* test(){yield 1})().drop(0).next().then(x => console.log(typeof x.value, x.value, typeof x.done, x.done))");
            Assert.assertEquals("number 1 boolean false\n", out.toString());
            out.reset();

            context.eval(JavaScriptLanguage.ID, "(async function* test(){yield 1})().drop(0).next.call((async function* test(){yield 1})()).catch(err => console.log(err))");
            Assert.assertTrue(out.toString().startsWith("TypeError: "));
        }
    }

    @Test
    public void testReturn() {
        AsyncInteropTest.TestOutput out = new AsyncInteropTest.TestOutput();
        Context.Builder builder = JSTest.newContextBuilder();
        builder.option(JSContextOptions.ITERATOR_HELPERS_NAME, "true");
        builder.out(out);
        try (Context context = builder.build()) {
            context.eval(JavaScriptLanguage.ID, "async function* test() {yield 1; yield 2;}; " +
                            "var x = test(); var y = x.drop(0); y.next().then(() => y.return()).then(() => x.next()).then(x => console.log(x.value, x.done))");
            Assert.assertEquals("undefined true\n", out.toString());
            out.reset();

            context.eval(JavaScriptLanguage.ID, "async function* test() {yield 1; yield 2;}; var x = test(); " +
                            "var y = test().flatMap(() => x); y.next().then(() => y.return()).then(() => x.next()).then(x => console.log(x.value, x.done))");
            Assert.assertEquals("undefined true\n", out.toString());
            out.reset();

            context.eval(JavaScriptLanguage.ID, "async function* test() {yield 1; yield 2;}; " +
                            "test().drop(0).return.call(test()).catch(err => console.log(err))");
            Assert.assertTrue(out.toString().startsWith("TypeError: "));
        }
    }
}
