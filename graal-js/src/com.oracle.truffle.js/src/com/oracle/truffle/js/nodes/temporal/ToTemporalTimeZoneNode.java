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

import static com.oracle.truffle.js.runtime.util.TemporalConstants.TIME_ZONE;
import static com.oracle.truffle.js.runtime.util.TemporalConstants.UTC;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.nodes.access.IsObjectNode;
import com.oracle.truffle.js.nodes.access.PropertyGetNode;
import com.oracle.truffle.js.nodes.cast.JSToStringNode;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.builtins.temporal.JSTemporalTimeZoneRecord;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.util.TemporalErrors;
import com.oracle.truffle.js.runtime.util.TemporalUtil;

/**
 * Implementation of ToTemporalTimeZone() operation.
 */
public abstract class ToTemporalTimeZoneNode extends JavaScriptBaseNode {

    private final ConditionProfile parseNameEmpty = ConditionProfile.createBinaryProfile();
    private final ConditionProfile parseIsZ = ConditionProfile.createBinaryProfile();
    private final ConditionProfile isObjectProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile isTimeZoneProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile hasProperty1Profile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile hasProperty2Profile = ConditionProfile.createBinaryProfile();
    private final BranchProfile errorBranch = BranchProfile.create();

    private final JSContext ctx;
    @Child protected PropertyGetNode getTimeZoneNode;

    protected ToTemporalTimeZoneNode(JSContext context) {
        this.ctx = context;
    }

    public static ToTemporalTimeZoneNode create(JSContext context) {
        return ToTemporalTimeZoneNodeGen.create(context);
    }

    public abstract JSDynamicObject executeDynamicObject(Object temporalTimeZoneLike);

    @Specialization
    protected JSDynamicObject toTemporalTimeZone(Object temporalTimeZoneLikeParam,
                    @Cached("create()") IsObjectNode isObjectNode,
                    @Cached("create()") JSToStringNode toStringNode) {
        Object temporalTimeZoneLike = temporalTimeZoneLikeParam;
        if (isObjectProfile.profile(isObjectNode.executeBoolean(temporalTimeZoneLike))) {
            JSDynamicObject tzObj = (JSDynamicObject) temporalTimeZoneLike;
            if (isTimeZoneProfile.profile(TemporalUtil.isTemporalZonedDateTime(tzObj))) {
                return (JSDynamicObject) getTimeZone(tzObj);
            } else if (hasProperty1Profile.profile(!JSObject.hasProperty(tzObj, TIME_ZONE))) {
                return tzObj;
            }
            temporalTimeZoneLike = getTimeZone(tzObj);
            if (hasProperty2Profile.profile(isObjectNode.executeBoolean(temporalTimeZoneLike) && !JSObject.hasProperty((JSDynamicObject) temporalTimeZoneLike, TIME_ZONE))) {
                return (JSDynamicObject) temporalTimeZoneLike;
            }
        }
        TruffleString identifier = toStringNode.executeString(temporalTimeZoneLike);
        JSTemporalTimeZoneRecord parseResult = TemporalUtil.parseTemporalTimeZoneString(identifier);
        if (parseNameEmpty.profile(parseResult.getName() != null)) {
            boolean canParse = TemporalUtil.canParseAsTimeZoneNumericUTCOffset(parseResult.getName());
            if (canParse) {
                if (parseResult.getOffsetString() != null && TemporalUtil.parseTimeZoneOffsetString(parseResult.getOffsetString()) != TemporalUtil.parseTimeZoneOffsetString(parseResult.getName())) {
                    errorBranch.enter();
                    throw TemporalErrors.createRangeErrorInvalidTimeZoneString();
                }
            } else {
                if (!TemporalUtil.isValidTimeZoneName(parseResult.getName())) {
                    errorBranch.enter();
                    throw TemporalErrors.createRangeErrorInvalidTimeZoneString();
                }
            }
            return TemporalUtil.createTemporalTimeZone(ctx, TemporalUtil.canonicalizeTimeZoneName(parseResult.getName()));
        }
        if (parseIsZ.profile(parseResult.isZ())) {
            return TemporalUtil.createTemporalTimeZone(ctx, UTC);
        }
        return TemporalUtil.createTemporalTimeZone(ctx, parseResult.getOffsetString());
    }

    private Object getTimeZone(JSDynamicObject obj) {
        if (getTimeZoneNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getTimeZoneNode = insert(PropertyGetNode.create(TIME_ZONE, false, ctx));
        }
        return getTimeZoneNode.getValue(obj);
    }
}
