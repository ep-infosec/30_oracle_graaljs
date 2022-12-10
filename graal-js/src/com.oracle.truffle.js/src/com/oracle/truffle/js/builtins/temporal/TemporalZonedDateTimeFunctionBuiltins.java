/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.builtins.temporal;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.builtins.JSBuiltinsContainer;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainDatePrototypeBuiltins.JSTemporalBuiltinOperation;
import com.oracle.truffle.js.builtins.temporal.TemporalZonedDateTimeFunctionBuiltinsFactory.JSTemporalZonedDateTimeCompareNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalZonedDateTimeFunctionBuiltinsFactory.JSTemporalZonedDateTimeFromNodeGen;
import com.oracle.truffle.js.nodes.function.JSBuiltin;
import com.oracle.truffle.js.nodes.function.JSBuiltinNode;
import com.oracle.truffle.js.nodes.temporal.ToTemporalZonedDateTimeNode;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.builtins.BuiltinEnum;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalZonedDateTime;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalZonedDateTimeObject;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.util.TemporalConstants;
import com.oracle.truffle.js.runtime.util.TemporalUtil;

public class TemporalZonedDateTimeFunctionBuiltins extends JSBuiltinsContainer.SwitchEnum<TemporalZonedDateTimeFunctionBuiltins.TemporalZonedDateTimeFunction> {

    public static final JSBuiltinsContainer BUILTINS = new TemporalZonedDateTimeFunctionBuiltins();

    protected TemporalZonedDateTimeFunctionBuiltins() {
        super(JSTemporalZonedDateTime.CLASS_NAME, TemporalZonedDateTimeFunction.class);
    }

    public enum TemporalZonedDateTimeFunction implements BuiltinEnum<TemporalZonedDateTimeFunction> {
        from(1),
        compare(2);

        private final int length;

        TemporalZonedDateTimeFunction(int length) {
            this.length = length;
        }

        @Override
        public int getLength() {
            return length;
        }
    }

    @Override
    protected Object createNode(JSContext context, JSBuiltin builtin, boolean construct, boolean newTarget, TemporalZonedDateTimeFunction builtinEnum) {
        switch (builtinEnum) {
            case from:
                return JSTemporalZonedDateTimeFromNodeGen.create(context, builtin, args().fixedArgs(2).createArgumentNodes(context));
            case compare:
                return JSTemporalZonedDateTimeCompareNodeGen.create(context, builtin, args().fixedArgs(2).createArgumentNodes(context));
        }
        return null;
    }

    public abstract static class JSTemporalZonedDateTimeFromNode extends JSTemporalBuiltinOperation {

        public JSTemporalZonedDateTimeFromNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected JSDynamicObject from(Object item, Object optionsParam,
                        @Cached("create(getContext())") ToTemporalZonedDateTimeNode toTemporalZonedDateTime,
                        @Cached TruffleString.EqualNode equalNode) {
            JSDynamicObject options = getOptionsObject(optionsParam);
            if (JSTemporalZonedDateTime.isJSTemporalZonedDateTime(item)) {
                JSTemporalZonedDateTimeObject zdt = (JSTemporalZonedDateTimeObject) item;
                TemporalUtil.toTemporalOverflow(options, getOptionNode());
                TemporalUtil.toTemporalDisambiguation(options, getOptionNode(), equalNode);
                TemporalUtil.toTemporalOffset(options, TemporalConstants.REJECT, getOptionNode(), equalNode);
                return JSTemporalZonedDateTime.create(getContext(), getRealm(), zdt.getNanoseconds(), zdt.getTimeZone(), zdt.getCalendar());
            }
            return toTemporalZonedDateTime.executeDynamicObject(item, options);
        }
    }

    public abstract static class JSTemporalZonedDateTimeCompareNode extends JSBuiltinNode {

        public JSTemporalZonedDateTimeCompareNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected int compare(Object obj1, Object obj2,
                        @Cached("create(getContext())") ToTemporalZonedDateTimeNode toTemporalZonedDateTime) {
            JSTemporalZonedDateTimeObject one = (JSTemporalZonedDateTimeObject) toTemporalZonedDateTime.executeDynamicObject(obj1, Undefined.instance);
            JSTemporalZonedDateTimeObject two = (JSTemporalZonedDateTimeObject) toTemporalZonedDateTime.executeDynamicObject(obj2, Undefined.instance);
            return TemporalUtil.compareEpochNanoseconds(one.getNanoseconds(), two.getNanoseconds());
        }
    }

}
