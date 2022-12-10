/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.test.regress;

import static com.oracle.truffle.js.lang.JavaScriptLanguage.ID;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.js.test.JSTest;

import org.graalvm.polyglot.PolyglotException;

public class GR19796 {

    @Test
    public void test() {
        try (Context context = JSTest.newContextBuilder().allowAllAccess(true).build()) {
            // Various objects with distinct InteropLibraries
            Value[] values = new Value[]{
                            context.eval(ID, "java.lang.Class"), // HostObject
                            context.eval(ID, "java.lang.Class.forName"), // HostFunction
                            context.getBindings(ID), // PolyglotLanguageBindings
                            context.eval(ID, "new Object()").getMetaObject(), // JSMetaObject
                            context.eval(ID, "Symbol()"), // Symbol
                            context.eval(ID, "42n") // BigInt
            };

            Value max = context.eval("js", "Math.max");

            // Activate JSToObjectArrayNode.doForeignObject() and exceed its cache limit
            for (Value value : values) {
                try {
                    max.invokeMember("apply", "this", value);
                } catch (PolyglotException pex) {
                    assertTrue(pex.isGuestException());
                }
            }

            // Ensure that undefined is handled correctly (i.e. not consumed by
            // JSToObjectArrayNode.doForeignObject()).
            Value result = max.invokeMember("apply", "this");
            // Math.max() === -Infinity
            assertTrue(result.isNumber());
            assertTrue(result.asDouble() == Double.NEGATIVE_INFINITY);
        }
    }
}
