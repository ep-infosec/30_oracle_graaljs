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
package com.oracle.truffle.js.test.instrumentation.sourcesections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

import org.graalvm.polyglot.PolyglotException;
import org.junit.After;
import org.junit.Before;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.js.test.instrumentation.FineGrainedAccessTest;
import com.oracle.truffle.js.test.instrumentation.TestUtil;
import com.oracle.truffle.js.test.instrumentation.TestingExecutionInstrument;

public class SourceSectionInstrumentationTest extends FineGrainedAccessTest {

    private ArrayList<CharSequence> sources = new ArrayList<>();

    private void initStatementInstrument(int maxEvents) {
        SourceSectionFilter stmFilter = SourceSectionFilter.newBuilder().tagIs(StatementTag.class).includeInternal(false).build();
        instrumenter.attachExecutionEventListener(stmFilter, new ExecutionEventListener() {
            @Override
            public void onEnter(EventContext cx, VirtualFrame frame) {
                if (sources.size() < maxEvents) {
                    sources.add(cx.getInstrumentedSourceSection().getCharacters());
                }
            }

            @Override
            public void onReturnValue(EventContext cx, VirtualFrame frame, Object result) {
            }

            @Override
            public void onReturnExceptional(EventContext cx, VirtualFrame frame, Throwable exception) {
            }
        });
    }

    protected void evalStatements(String src) {
        initStatementInstrument(Integer.MAX_VALUE);
        context.eval("js", src);
    }

    protected void evalMaxStatementsTimeout(String src, int maxEvents, int timeout) {
        initStatementInstrument(maxEvents);
        context.initialize("js");
        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                context.close(true);
            }
        }, timeout);
        try {
            context.eval("js", src);
            assert false;
        } catch (PolyglotException e) {
            assert e.isCancelled();
        }
    }

    protected void evalExpressions(String src) {
        SourceSectionFilter expFilter = SourceSectionFilter.newBuilder().tagIs(ExpressionTag.class).includeInternal(false).build();
        instrumenter.attachExecutionEventListener(expFilter, new ExecutionEventListener() {
            @Override
            public void onEnter(EventContext cx, VirtualFrame frame) {
            }

            @Override
            public void onReturnValue(EventContext cx, VirtualFrame frame, Object result) {
                sources.add(cx.getInstrumentedSourceSection().getCharacters());
            }

            @Override
            public void onReturnExceptional(EventContext cx, VirtualFrame frame, Throwable exception) {
            }
        });
        context.eval("js", src);
    }

    public final void assertSourceSections(String[] expected) {
        int i = 0;
        assertEquals(sources.stream().collect(Collectors.joining("\n")), expected.length, sources.size());
        for (String s : expected) {
            assertEquals(s, sources.get(i++));
        }
    }

    @After
    @Override
    public void disposeAgent() {
        assertTrue(!sources.isEmpty());
        context.leave();
        sources.clear();
    }

    @Override
    @Before
    public void initTest() {
        context = TestUtil.newContextBuilder().build();

        instrument = context.getEngine().getInstruments().get(TestingExecutionInstrument.ID).lookup(TestingExecutionInstrument.class);
        instrumenter = instrument.getEnvironment().getInstrumenter();

        sources = new ArrayList<>();
        context.enter();
    }

}
