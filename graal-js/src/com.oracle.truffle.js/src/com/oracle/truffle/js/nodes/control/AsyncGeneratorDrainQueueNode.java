/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.nodes.control;

import java.util.ArrayDeque;

import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.objects.AsyncGeneratorRequest;
import com.oracle.truffle.js.runtime.objects.Completion;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.JSObjectUtil;

public class AsyncGeneratorDrainQueueNode extends AsyncGeneratorAwaitReturnNode {

    AsyncGeneratorDrainQueueNode(JSContext context) {
        super(context);
    }

    public static AsyncGeneratorDrainQueueNode create(JSContext context) {
        return new AsyncGeneratorDrainQueueNode(context);
    }

    @SuppressWarnings("unchecked")
    public final void asyncGeneratorCompleteStepAndDrainQueue(VirtualFrame frame, Object generator, Completion.Type resultType, Object resultValue) {
        ArrayDeque<AsyncGeneratorRequest> queue = (ArrayDeque<AsyncGeneratorRequest>) getGeneratorQueue.getValue(generator);
        setGeneratorState.setValue(generator, JSFunction.AsyncGeneratorState.Completed);
        asyncGeneratorCompleteStep(frame, resultType, resultValue, true, queue);
        if (!queue.isEmpty()) {
            asyncGeneratorDrainQueue(frame, generator, queue);
        }
    }

    public final void asyncGeneratorDrainQueue(VirtualFrame frame, Object generator, ArrayDeque<AsyncGeneratorRequest> queue) {
        Object state;
        assert (state = JSObjectUtil.getHiddenProperty((JSDynamicObject) generator, JSFunction.ASYNC_GENERATOR_STATE_ID)) == JSFunction.AsyncGeneratorState.Completed : state;
        while (!queue.isEmpty()) {
            AsyncGeneratorRequest next = queue.peekFirst();
            if (next.getCompletionType() == Completion.Type.Return) {
                setGeneratorState.setValue(generator, JSFunction.AsyncGeneratorState.AwaitingReturn);
                try {
                    asyncGeneratorAwaitReturn(generator, queue);
                    break;
                } catch (AbstractTruffleException ex) {
                    // PromiseResolve has thrown an error
                    asyncGeneratorRejectBrokenPromise(frame, generator, ex, queue);
                }
            } else {
                asyncGeneratorCompleteStep(frame, next.getCompletionType(), next.getCompletionValue(), true, queue);
            }
        }
    }
}
