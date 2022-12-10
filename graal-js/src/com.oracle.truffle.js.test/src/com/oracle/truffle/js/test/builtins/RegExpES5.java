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
package com.oracle.truffle.js.test.builtins;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.runtime.JSContextOptions;
import com.oracle.truffle.js.test.JSTest;

/**
 * String.prototype.split, String.prototype.match, String.prototype.search, and
 * String.prototype.replace show special behavior in ES5.
 *
 */
public class RegExpES5 {

    private static void test(String sourceText) {
        Assert.assertTrue(testIntl(sourceText));
    }

    private static boolean testIntl(String sourceText) {
        try (Context context = JSTest.newContextBuilder().option(JSContextOptions.ECMASCRIPT_VERSION_NAME, "5").build()) {
            Value result = context.eval(Source.newBuilder(JavaScriptLanguage.ID, sourceText, "regexp-es5-test").buildLiteral());
            return result.asBoolean();
        }
    }

    @Test
    public void testSplitES5() {
        test("'ab_cd_ef'.split(/_/).length === 3");
        test("'abcdef'.split(/_/).length === 1");

        test("'ab_cd_ef'.split('_').length === 3");
        test("'ab_cd_ef'.split().length === 1");
    }

    @Test
    public void testMatchES5() {
        test("'ab_cd_ef'.match(/_/).length === 1");
        test("'ab_cd_ef'.match(/_/g).length === 2");
        test("'abcdef'.match(/_/) === null");

        test("'ab_cd_ef'.match('_').length === 1");
        test("'ab_cd_ef'.match().length === 1");
        test("'abcdef'.match('X') === null");
    }

    @Test
    public void testSearchES5() {
        test("'ab_cd_ef'.search(/_/) === 2");
        test("'abcdef'.search(/_/) === -1");
        test("'abcdef'.search() === 0");
    }

    @Test
    public void testReplaceES5() {
        test("'ab_cd_ef'.replace(/_/, 'X') === 'abXcd_ef'");
        test("'ab_cd_ef'.replace(/_/g, 'X') === 'abXcdXef'");
        test("'abcdef'.replace(/_/) === 'abcdef'");

        test("'ab_cd_ef'.replace('_', 'X') === 'abXcd_ef'");
        test("'ab_cd_ef'.replace(undefined, 'X') === 'ab_cd_ef'");
        test("'abcdef'.replace('X', 'X') === 'abcdef'");

        // with substitution
        test("'ab_cd_ef'.replace(/_/, 'X$$') === 'abX$cd_ef'");
        test("'ab_cd_ef'.replace(/_/, 'X$&') === 'abX_cd_ef'");
        test("'ab_cd_ef'.replace(/_/, 'X$\\'') === 'abXcd_efcd_ef'");
        test("'ab_cd_ef'.replace(/(_)/, 'X$1') === 'abX_cd_ef'");
    }

}
