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

import static com.oracle.truffle.js.runtime.util.TemporalConstants.CALENDAR;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.OVERFLOW;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.REJECT;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.TIME_ZONE;

import java.util.EnumSet;
import java.util.List;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.builtins.JSBuiltinsContainer;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainDatePrototypeBuiltins.JSTemporalBuiltinOperation;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainMonthDayPrototypeBuiltinsFactory.JSTemporalPlainMonthDayEqualsNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainMonthDayPrototypeBuiltinsFactory.JSTemporalPlainMonthDayGetISOFieldsNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainMonthDayPrototypeBuiltinsFactory.JSTemporalPlainMonthDayGetterNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainMonthDayPrototypeBuiltinsFactory.JSTemporalPlainMonthDayToLocaleStringNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainMonthDayPrototypeBuiltinsFactory.JSTemporalPlainMonthDayToPlainDateNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainMonthDayPrototypeBuiltinsFactory.JSTemporalPlainMonthDayToStringNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainMonthDayPrototypeBuiltinsFactory.JSTemporalPlainMonthDayValueOfNodeGen;
import com.oracle.truffle.js.builtins.temporal.TemporalPlainMonthDayPrototypeBuiltinsFactory.JSTemporalPlainMonthDayWithNodeGen;
import com.oracle.truffle.js.nodes.access.EnumerableOwnPropertyNamesNode;
import com.oracle.truffle.js.nodes.cast.JSToStringNode;
import com.oracle.truffle.js.nodes.function.JSBuiltin;
import com.oracle.truffle.js.nodes.function.JSBuiltinNode;
import com.oracle.truffle.js.nodes.temporal.TemporalCalendarDateFromFieldsNode;
import com.oracle.truffle.js.nodes.temporal.TemporalCalendarFieldsNode;
import com.oracle.truffle.js.nodes.temporal.TemporalCalendarGetterNode;
import com.oracle.truffle.js.nodes.temporal.TemporalMonthDayFromFieldsNode;
import com.oracle.truffle.js.nodes.temporal.ToTemporalMonthDayNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.builtins.BuiltinEnum;
import com.oracle.truffle.js.runtime.builtins.JSOrdinary;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainMonthDay;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalPlainMonthDayObject;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.util.TemporalConstants;
import com.oracle.truffle.js.runtime.util.TemporalErrors;
import com.oracle.truffle.js.runtime.util.TemporalUtil;
import com.oracle.truffle.js.runtime.util.TemporalUtil.ShowCalendar;

public class TemporalPlainMonthDayPrototypeBuiltins extends JSBuiltinsContainer.SwitchEnum<TemporalPlainMonthDayPrototypeBuiltins.TemporalPlainMonthDayPrototype> {

    public static final JSBuiltinsContainer BUILTINS = new TemporalPlainMonthDayPrototypeBuiltins();

    protected TemporalPlainMonthDayPrototypeBuiltins() {
        super(JSTemporalPlainMonthDay.PROTOTYPE_NAME, TemporalPlainMonthDayPrototype.class);
    }

    public enum TemporalPlainMonthDayPrototype implements BuiltinEnum<TemporalPlainMonthDayPrototype> {
        // getters
        calendar(0),
        monthCode(0),
        day(0),

        // methods
        with(1),
        equals(1),
        toPlainDate(1),
        getISOFields(0),
        toString(0),
        toLocaleString(0),
        toJSON(0),
        valueOf(0);

        private final int length;

        TemporalPlainMonthDayPrototype(int length) {
            this.length = length;
        }

        @Override
        public int getLength() {
            return length;
        }

        @Override
        public boolean isGetter() {
            return EnumSet.of(calendar, monthCode, day).contains(this);
        }
    }

    @Override
    protected Object createNode(JSContext context, JSBuiltin builtin, boolean construct, boolean newTarget, TemporalPlainMonthDayPrototype builtinEnum) {
        switch (builtinEnum) {
            case calendar:
            case monthCode:
            case day:
                return JSTemporalPlainMonthDayGetterNodeGen.create(context, builtin, builtinEnum, args().withThis().createArgumentNodes(context));

            case with:
                return JSTemporalPlainMonthDayWithNodeGen.create(context, builtin, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case equals:
                return JSTemporalPlainMonthDayEqualsNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case toPlainDate:
                return JSTemporalPlainMonthDayToPlainDateNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case getISOFields:
                return JSTemporalPlainMonthDayGetISOFieldsNodeGen.create(context, builtin, args().withThis().createArgumentNodes(context));
            case toString:
                return JSTemporalPlainMonthDayToStringNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case toLocaleString:
            case toJSON:
                return JSTemporalPlainMonthDayToLocaleStringNodeGen.create(context, builtin, args().withThis().createArgumentNodes(context));
            case valueOf:
                return JSTemporalPlainMonthDayValueOfNodeGen.create(context, builtin, args().withThis().createArgumentNodes(context));
        }
        return null;
    }

    public abstract static class JSTemporalPlainMonthDayGetterNode extends JSBuiltinNode {

        public final TemporalPlainMonthDayPrototype property;

        public JSTemporalPlainMonthDayGetterNode(JSContext context, JSBuiltin builtin, TemporalPlainMonthDayPrototype property) {
            super(context, builtin);
            this.property = property;
        }

        @Specialization(guards = "isJSTemporalMonthDay(thisObj)")
        protected Object monthDayGetter(Object thisObj,
                        @Cached("create(getContext())") TemporalCalendarGetterNode calendarGetterNode) {
            JSTemporalPlainMonthDayObject plainMD = (JSTemporalPlainMonthDayObject) thisObj;
            switch (property) {
                case day:
                    return TemporalUtil.calendarDay(calendarGetterNode, plainMD.getCalendar(), plainMD);
                case monthCode:
                    return TemporalUtil.calendarMonthCode(calendarGetterNode, plainMD.getCalendar(), plainMD);
                case calendar:
                    return plainMD.getCalendar();
            }
            throw Errors.shouldNotReachHere();
        }

        @Specialization(guards = "!isJSTemporalMonthDay(thisObj)")
        protected static int error(@SuppressWarnings("unused") Object thisObj) {
            throw TemporalErrors.createTypeErrorTemporalPlainMonthDayExpected();
        }
    }

    // 4.3.20
    public abstract static class JSTemporalPlainMonthDayToString extends JSTemporalBuiltinOperation {

        protected JSTemporalPlainMonthDayToString(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected TruffleString toString(Object thisObj, Object optParam,
                        @Cached TruffleString.EqualNode equalNode) {
            JSTemporalPlainMonthDayObject md = requireTemporalMonthDay(thisObj);
            JSDynamicObject options = getOptionsObject(optParam);
            ShowCalendar showCalendar = TemporalUtil.toShowCalendarOption(options, getOptionNode(), equalNode);
            return JSTemporalPlainMonthDay.temporalMonthDayToString(md, showCalendar);
        }
    }

    public abstract static class JSTemporalPlainMonthDayToLocaleString extends JSTemporalBuiltinOperation {

        protected JSTemporalPlainMonthDayToLocaleString(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        public TruffleString toLocaleString(Object thisObj) {
            JSTemporalPlainMonthDayObject time = requireTemporalMonthDay(thisObj);
            return JSTemporalPlainMonthDay.temporalMonthDayToString(time, ShowCalendar.AUTO);
        }
    }

    public abstract static class JSTemporalPlainMonthDayValueOf extends JSTemporalBuiltinOperation {

        protected JSTemporalPlainMonthDayValueOf(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Object valueOf(@SuppressWarnings("unused") Object thisObj) {
            throw Errors.createTypeError("Not supported.");
        }
    }

    public abstract static class JSTemporalPlainMonthDayToPlainDateNode extends JSTemporalBuiltinOperation {

        protected JSTemporalPlainMonthDayToPlainDateNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Object toPlainDate(Object thisObj, Object item,
                        @Cached("createKeys(getContext())") EnumerableOwnPropertyNamesNode namesNode,
                        @Cached("create(getContext())") TemporalCalendarFieldsNode calendarFieldsNode,
                        @Cached("create(getContext())") TemporalCalendarDateFromFieldsNode dateFromFieldsNode) {
            JSTemporalPlainMonthDayObject monthDay = requireTemporalMonthDay(thisObj);
            if (!isObject(item)) {
                errorBranch.enter();
                throw TemporalErrors.createTypeErrorTemporalPlainMonthDayExpected();
            }
            JSDynamicObject calendar = monthDay.getCalendar();
            List<TruffleString> receiverFieldNames = calendarFieldsNode.execute(calendar, TemporalUtil.listDMC);
            JSDynamicObject fields = TemporalUtil.prepareTemporalFields(getContext(), monthDay, receiverFieldNames, TemporalUtil.listEmpty);
            List<TruffleString> inputFieldNames = calendarFieldsNode.execute(calendar, TemporalUtil.listY);
            JSDynamicObject inputFields = TemporalUtil.prepareTemporalFields(getContext(), TemporalUtil.toJSDynamicObject(item, errorBranch), inputFieldNames, TemporalUtil.listEmpty);
            JSDynamicObject mergedFields = TemporalUtil.calendarMergeFields(getContext(), namesNode, errorBranch, calendar, fields, inputFields);
            List<TruffleString> mergedFieldNames = TemporalUtil.listJoinRemoveDuplicates(receiverFieldNames, inputFieldNames);
            mergedFields = TemporalUtil.prepareTemporalFields(getContext(), mergedFields, mergedFieldNames, TemporalUtil.listEmpty);
            JSDynamicObject options = JSOrdinary.createWithNullPrototype(getContext());
            TemporalUtil.createDataPropertyOrThrow(getContext(), options, OVERFLOW, REJECT);
            return dateFromFieldsNode.execute(calendar, mergedFields, options);
        }
    }

    public abstract static class JSTemporalPlainMonthDayGetISOFields extends JSTemporalBuiltinOperation {

        protected JSTemporalPlainMonthDayGetISOFields(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        public JSDynamicObject getISOFields(Object thisObj) {
            JSTemporalPlainMonthDayObject md = requireTemporalMonthDay(thisObj);
            JSObject obj = JSOrdinary.create(getContext(), getRealm());
            TemporalUtil.createDataPropertyOrThrow(getContext(), obj, CALENDAR, md.getCalendar());
            TemporalUtil.createDataPropertyOrThrow(getContext(), obj, TemporalConstants.ISO_DAY, md.getDay());
            TemporalUtil.createDataPropertyOrThrow(getContext(), obj, TemporalConstants.ISO_MONTH, md.getMonth());
            TemporalUtil.createDataPropertyOrThrow(getContext(), obj, TemporalConstants.ISO_YEAR, md.getYear());
            return obj;
        }
    }

    public abstract static class JSTemporalPlainMonthDayWithNode extends JSTemporalBuiltinOperation {

        protected JSTemporalPlainMonthDayWithNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected JSDynamicObject with(Object thisObj, Object temporalMonthDayLike, Object optParam,
                        @Cached("createKeys(getContext())") EnumerableOwnPropertyNamesNode namesNode,
                        @Cached("create(getContext())") TemporalMonthDayFromFieldsNode monthDayFromFieldsNode,
                        @Cached("create(getContext())") TemporalCalendarFieldsNode calendarFieldsNode) {
            JSTemporalPlainMonthDayObject md = requireTemporalMonthDay(thisObj);
            if (!isObject(temporalMonthDayLike)) {
                errorBranch.enter();
                throw Errors.createTypeError("Object expected");
            }
            JSDynamicObject mdLikeObj = (JSDynamicObject) temporalMonthDayLike;
            TemporalUtil.rejectTemporalCalendarType(mdLikeObj, errorBranch);
            Object calendarProperty = JSObject.get(mdLikeObj, CALENDAR);
            if (calendarProperty != Undefined.instance) {
                errorBranch.enter();
                throw TemporalErrors.createTypeErrorUnexpectedCalendar();
            }
            Object timezoneProperty = JSObject.get(mdLikeObj, TIME_ZONE);
            if (timezoneProperty != Undefined.instance) {
                errorBranch.enter();
                throw TemporalErrors.createTypeErrorUnexpectedTimeZone();
            }
            JSDynamicObject calendar = md.getCalendar();
            List<TruffleString> fieldNames = calendarFieldsNode.execute(calendar, TemporalUtil.listDMMCY);
            JSDynamicObject partialMonthDay = TemporalUtil.preparePartialTemporalFields(getContext(), mdLikeObj, fieldNames);
            JSDynamicObject options = getOptionsObject(optParam);
            JSDynamicObject fields = TemporalUtil.prepareTemporalFields(getContext(), md, fieldNames, TemporalUtil.listEmpty);
            fields = TemporalUtil.calendarMergeFields(getContext(), namesNode, errorBranch, calendar, fields, partialMonthDay);
            fields = TemporalUtil.prepareTemporalFields(getContext(), fields, fieldNames, TemporalUtil.listEmpty);
            return monthDayFromFieldsNode.execute(calendar, fields, options);
        }
    }

    public abstract static class JSTemporalPlainMonthDayEqualsNode extends JSTemporalBuiltinOperation {

        protected JSTemporalPlainMonthDayEqualsNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected boolean equals(Object thisObj, Object otherParam,
                        @Cached JSToStringNode toStringNode,
                        @Cached("create(getContext())") ToTemporalMonthDayNode toTemporalMonthDayNode) {
            JSTemporalPlainMonthDayObject md = requireTemporalMonthDay(thisObj);
            JSTemporalPlainMonthDayObject other = toTemporalMonthDayNode.executeDynamicObject(otherParam, Undefined.instance);
            if (md.getMonth() != other.getMonth()) {
                return false;
            }
            if (md.getDay() != other.getDay()) {
                return false;
            }
            if (md.getYear() != other.getYear()) {
                return false;
            }
            return TemporalUtil.calendarEquals(md.getCalendar(), other.getCalendar(), toStringNode);
        }
    }
}
