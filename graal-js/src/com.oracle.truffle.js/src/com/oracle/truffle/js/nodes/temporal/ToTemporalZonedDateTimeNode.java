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
package com.oracle.truffle.js.nodes.temporal;

import static com.oracle.truffle.js.runtime.util.TemporalConstants.OFFSET;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.REJECT;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.TIME_ZONE;

import java.util.List;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.nodes.access.IsObjectNode;
import com.oracle.truffle.js.nodes.cast.JSToStringNode;
import com.oracle.truffle.js.runtime.BigInt;
import com.oracle.truffle.js.runtime.Boundaries;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalDateTimeRecord;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalZonedDateTime;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalZonedDateTimeRecord;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.util.TemporalErrors;
import com.oracle.truffle.js.runtime.util.TemporalUtil;
import com.oracle.truffle.js.runtime.util.TemporalUtil.MatchBehaviour;
import com.oracle.truffle.js.runtime.util.TemporalUtil.OffsetBehaviour;
import com.oracle.truffle.js.runtime.util.TemporalUtil.OffsetOption;

/**
 * Implementation of ToTemporalZonedDateTime() operation.
 */
public abstract class ToTemporalZonedDateTimeNode extends JavaScriptBaseNode {

    private final BranchProfile errorBranch = BranchProfile.create();
    private final ConditionProfile isObjectProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile isZonedDateTimeProfile = ConditionProfile.createBinaryProfile();

    protected final JSContext ctx;

    protected ToTemporalZonedDateTimeNode(JSContext context) {
        this.ctx = context;
    }

    public static ToTemporalZonedDateTimeNode create(JSContext context) {
        return ToTemporalZonedDateTimeNodeGen.create(context);
    }

    public abstract JSDynamicObject executeDynamicObject(Object value, JSDynamicObject options);

    @Specialization
    public JSDynamicObject toTemporalZonedDateTime(Object item, JSDynamicObject options,
                    @Cached("create()") IsObjectNode isObjectNode,
                    @Cached("create()") JSToStringNode toStringNode,
                    @Cached("create()") TemporalGetOptionNode getOptionNode,
                    @Cached("create(ctx)") ToTemporalTimeZoneNode toTemporalTimeZone,
                    @Cached("create(ctx)") GetTemporalCalendarWithISODefaultNode getTemporalCalendarNode,
                    @Cached("create(ctx)") ToTemporalCalendarWithISODefaultNode toTemporalCalendarWithISODefaultNode,
                    @Cached("create(ctx)") TemporalCalendarFieldsNode calendarFieldsNode,
                    @Cached TruffleString.EqualNode equalNode,
                    @Cached("create(ctx)") TemporalCalendarDateFromFieldsNode dateFromFieldsNode) {
        assert options != null;
        JSTemporalDateTimeRecord result;
        TruffleString offsetString = null;
        JSDynamicObject timeZone = null;
        JSDynamicObject calendar = null;
        JSRealm realm = JSRealm.get(this);
        OffsetBehaviour offsetBehaviour = OffsetBehaviour.OPTION;
        MatchBehaviour matchBehaviour = MatchBehaviour.MATCH_EXACTLY;
        if (isObjectProfile.profile(isObjectNode.executeBoolean(item))) {
            JSDynamicObject itemObj = (JSDynamicObject) item;
            if (isZonedDateTimeProfile.profile(TemporalUtil.isTemporalZonedDateTime(itemObj))) {
                return itemObj;
            }
            calendar = getTemporalCalendarNode.executeDynamicObject(itemObj);
            List<TruffleString> fieldNames = calendarFieldsNode.execute(calendar, TemporalUtil.listDHMMMMMNSY);
            Boundaries.listAdd(fieldNames, TIME_ZONE);
            Boundaries.listAdd(fieldNames, OFFSET);
            JSDynamicObject fields = TemporalUtil.prepareTemporalFields(ctx, itemObj, fieldNames, TemporalUtil.listTimeZone);
            Object timeZoneObj = JSObject.get(fields, TIME_ZONE);
            timeZone = toTemporalTimeZone.executeDynamicObject(timeZoneObj);
            Object offsetStringObj = JSObject.get(fields, OFFSET);
            if (offsetStringObj == Undefined.instance) {
                offsetBehaviour = OffsetBehaviour.WALL;
            } else {
                offsetString = toStringNode.executeString(offsetStringObj);
            }
            result = TemporalUtil.interpretTemporalDateTimeFields(calendar, fields, options, getOptionNode, dateFromFieldsNode);
        } else {
            TemporalUtil.toTemporalOverflow(options, getOptionNode);
            TruffleString string = toStringNode.executeString(item);
            JSTemporalZonedDateTimeRecord resultZDT = TemporalUtil.parseTemporalZonedDateTimeString(string);
            result = resultZDT;
            TruffleString timeZoneName = resultZDT.getTimeZoneName();
            assert timeZoneName != null;
            if (!TemporalUtil.canParseAsTimeZoneNumericUTCOffset(timeZoneName)) {
                if (!TemporalUtil.isValidTimeZoneName(timeZoneName)) {
                    errorBranch.enter();
                    throw TemporalErrors.createRangeErrorInvalidTimeZoneString();
                }
                timeZoneName = TemporalUtil.canonicalizeTimeZoneName(timeZoneName);
            }
            offsetString = resultZDT.getTimeZoneOffsetString();
            if (resultZDT.getTimeZoneZ()) {
                offsetBehaviour = OffsetBehaviour.EXACT;
            } else {
                offsetBehaviour = OffsetBehaviour.WALL;
            }
            timeZone = TemporalUtil.createTemporalTimeZone(ctx, timeZoneName);
            calendar = toTemporalCalendarWithISODefaultNode.executeDynamicObject(result.getCalendar());
            matchBehaviour = MatchBehaviour.MATCH_MINUTES;
        }
        long offsetNanoseconds = 0;
        if (offsetBehaviour == OffsetBehaviour.OPTION) {
            offsetNanoseconds = TemporalUtil.parseTimeZoneOffsetString(offsetString);
        }
        TemporalUtil.Disambiguation disambiguation = TemporalUtil.toTemporalDisambiguation(options, getOptionNode, equalNode);
        OffsetOption offset = TemporalUtil.toTemporalOffset(options, REJECT, getOptionNode, equalNode);
        BigInt epochNanoseconds = TemporalUtil.interpretISODateTimeOffset(ctx, realm, result.getYear(), result.getMonth(), result.getDay(), result.getHour(), result.getMinute(),
                        result.getSecond(), result.getMillisecond(), result.getMicrosecond(), result.getNanosecond(), offsetBehaviour, offsetNanoseconds, timeZone, disambiguation, offset,
                        matchBehaviour);
        return JSTemporalZonedDateTime.create(ctx, getRealm(), epochNanoseconds, timeZone, calendar);
    }
}
