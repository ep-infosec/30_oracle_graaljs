/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.test.debug;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.js.test.JSTest;

public class DebugBuiltinsTest {

    @Test
    public void testDebugBuiltin() {
        Context ctx = JSTest.newContextBuilder().option("js.debug-builtin", "true").build();
        ctx.eval("js", "Debug.class(); Debug.class({}); Debug.class([]);");
        ctx.eval("js", "Debug.className(); Debug.className({}); Debug.className([]);");
        ctx.eval("js", "Debug.getClass(); Debug.getClass({}); Debug.getClass([]);");
        ctx.eval("js", "Debug.shape(); Debug.shape({}); Debug.shape([]);");
        ctx.eval("js", "Debug.dumpCounters();");
        ctx.eval("js", "Debug.dumpFunctionTree(()=>{return true;});");
        ctx.eval("js", "Debug.printObject(); Debug.printObject({}); Debug.printObject([]);");
        ctx.eval("js", "Debug.toJavaString(); Debug.toJavaString({}); Debug.toJavaString([]);");
        ctx.eval("js", "Debug.arraytype(); Debug.arraytype([]); Debug.arraytype({});");
        ctx.eval("js", "Debug.srcattr(()=>{return true;});");
        ctx.eval("js", "Debug.assertInt(1); Debug.assertInt(2); Debug.assertInt(3);");
        ctx.eval("js", "Debug.continueInInterpreter();");
        ctx.eval("js", "Debug.stringCompare('test', 'test'); Debug.stringCompare('test','wrong');");
        ctx.eval("js", "Debug.isHolesArray([]); Debug.isHolesArray({})");
        ctx.eval("js", "Debug.jsStack();");
        // ctx.eval("js", "Debug.loadModule('test',{});"); //called from another test
        ctx.eval("js", "Debug.createSafeInteger(123);");
        ctx.eval("js", "Debug.typedArrayDetachBuffer(new ArrayBuffer(100));");
        ctx.eval("js", "Debug.systemGC();");
        ctx.eval("js", "Debug.systemProperty();");
        ctx.eval("js", "Debug.systemProperties();");
        ctx.eval("js", "Debug.neverPartOfCompilation();");

        String heapDumpName = ctx.eval("js", "Debug.dumpHeap();").asString();
        File heapDump = new File(heapDumpName);
        assertTrue(heapDump.exists());
        assertTrue(heapDump.delete());

        ctx.close();
    }

}
