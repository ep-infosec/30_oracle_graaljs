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
package com.oracle.truffle.js.runtime.builtins.temporal;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.js.runtime.BigInt;
import com.oracle.truffle.js.runtime.objects.JSNonProxyObject;
import com.oracle.truffle.js.runtime.util.TemporalUtil;

@ExportLibrary(InteropLibrary.class)
public class JSTemporalInstantObject extends JSNonProxyObject {

    private final BigInt nanoseconds; // 8.4 A BigInt value

    protected JSTemporalInstantObject(Shape shape, BigInt nanoseconds) {
        super(shape);
        this.nanoseconds = nanoseconds;
    }

    public BigInt getNanoseconds() {
        return nanoseconds;
    }

    @ExportMessage
    @TruffleBoundary
    Instant asInstant() {
        BigInteger[] res = nanoseconds.bigIntegerValue().divideAndRemainder(TemporalUtil.BI_10_POW_9);
        return Instant.ofEpochSecond(res[0].longValue(), res[1].intValue());
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    final boolean isTimeZone() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    @SuppressWarnings("static-method")
    final ZoneId asTimeZone() {
        return ZoneId.of("UTC");
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    final boolean isDate() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    final LocalDate asDate() {
        LocalDate ld = LocalDate.ofInstant(asInstant(), asTimeZone());
        return ld;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    final boolean isTime() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    final LocalTime asTime() {
        LocalTime lt = LocalTime.ofInstant(asInstant(), asTimeZone());
        return lt;
    }

}
