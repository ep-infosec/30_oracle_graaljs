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
package com.oracle.truffle.js.stats;

import static com.oracle.truffle.js.shell.JSLauncher.PreprocessResult.Consumed;

import java.io.File;
import java.io.IOException;

import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;

import com.oracle.truffle.js.builtins.helper.HeapDump;
import com.oracle.truffle.js.runtime.JSConfig;
import com.oracle.truffle.js.shell.RepeatingLauncher;
import com.oracle.truffle.js.stats.heap.HeapDumpAnalyzer;

public class ShellWithStats extends RepeatingLauncher {
    private boolean heapDump = false;
    private String heapDumpFileName = null;

    public static void main(String[] args) {
        new ShellWithStats().launch(args);
    }

    @Override
    protected PreprocessResult preprocessArgument(String argument) {
        if (argument.equals("heap-dump")) {
            heapDump = true;
            return Consumed;
        }
        if (argument.startsWith("heap-dump-file")) {
            heapDumpFileName = argument.substring(argument.indexOf("=") + 1);
            return Consumed;
        }
        return super.preprocessArgument(argument);
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {
        super.printHelp(maxCategory);
        printOption("--heap-dump", "take a heap dump at the end of the execution");
    }

    @Override
    protected int executeScripts(Context.Builder contextBuilder) {
        int result = super.executeScripts(contextBuilder);
        if (!JSConfig.SubstrateVM && heapDump) {
            try {
                String dumpName = heapDumpFileName == null ? HeapDump.defaultDumpName() : heapDumpFileName;
                deleteIfExists(dumpName);
                System.out.println("Dumping the heap to: " + dumpName);
                HeapDump.dump(dumpName, true);
                HeapDumpAnalyzer.main(new String[]{dumpName});
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    private static void deleteIfExists(String dumpName) {
        File dumpFile = new File(dumpName);
        if (dumpFile.exists()) {
            System.out.println("Deleting existing file: " + dumpName);
            if (!dumpFile.delete()) {
                System.out.println("could not delete");
            }
        }
    }
}
