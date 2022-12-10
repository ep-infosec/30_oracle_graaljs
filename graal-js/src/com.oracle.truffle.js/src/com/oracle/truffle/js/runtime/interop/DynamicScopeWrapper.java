/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.runtime.interop;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.nodes.interop.ExportValueNode;
import com.oracle.truffle.js.runtime.Properties;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.objects.Dead;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.JSProperty;

/**
 * Wraps a dynamic scope object, filters out dead variables, and prevents const assignment.
 */
@ExportLibrary(InteropLibrary.class)
public final class DynamicScopeWrapper implements TruffleObject {
    final JSDynamicObject scope;

    public DynamicScopeWrapper(JSDynamicObject scope) {
        this.scope = scope;
    }

    boolean isConst(TruffleString name, DynamicObjectLibrary access) {
        return JSProperty.isConst(Properties.getProperty(access, scope, name));
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal,
                    @CachedLibrary("this.scope") DynamicObjectLibrary access) {
        List<String> keys = new ArrayList<>();
        for (Object key : access.getKeyArray(scope)) {
            if (Strings.isTString(key)) {
                Object value = Properties.getOrDefault(access, scope, key, null);
                if (value != null && value != Dead.instance()) {
                    keys.add(Strings.toJavaString((TruffleString) key));
                }
            }
        }
        return InteropList.create(keys);
    }

    @ExportMessage
    boolean isMemberReadable(String name,
                    @Cached @Cached.Shared("fromJavaStringNode") TruffleString.FromJavaStringNode fromJavaStringNode,
                    @CachedLibrary("this.scope") DynamicObjectLibrary access) {
        TruffleString tsName = Strings.fromJavaString(fromJavaStringNode, name);
        return isMemberReadableIntl(tsName, access);
    }

    private boolean isMemberReadableIntl(TruffleString tsName, DynamicObjectLibrary access) {
        Object value = Properties.getOrDefault(access, scope, tsName, null);
        if (value == null || value == Dead.instance()) {
            return false;
        }
        return true;
    }

    @ExportMessage
    boolean isMemberModifiable(String name,
                    @Cached @Cached.Shared("fromJavaStringNode") TruffleString.FromJavaStringNode fromJavaStringNode,
                    @CachedLibrary("this.scope") DynamicObjectLibrary access) {
        TruffleString tsName = Strings.fromJavaString(fromJavaStringNode, name);
        return isMemberReadableIntl(tsName, access) && !isConst(tsName, access);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isMemberInsertable(@SuppressWarnings("unused") String name) {
        return false;
    }

    @ExportMessage
    Object readMember(String name,
                    @Cached @Cached.Shared("fromJavaStringNode") TruffleString.FromJavaStringNode fromJavaStringNode,
                    @CachedLibrary("this.scope") DynamicObjectLibrary access,
                    @Cached ExportValueNode exportValueNode) throws UnknownIdentifierException {
        TruffleString tsName = Strings.fromJavaString(fromJavaStringNode, name);
        Object value = Properties.getOrDefault(access, scope, tsName, null);
        if (value == null || value == Dead.instance()) {
            throw UnknownIdentifierException.create(name);
        } else {
            return exportValueNode.execute(value);
        }
    }

    @ExportMessage
    void writeMember(String name, Object value,
                    @Cached @Cached.Shared("fromJavaStringNode") TruffleString.FromJavaStringNode fromJavaStringNode,
                    @CachedLibrary("this.scope") DynamicObjectLibrary access) throws UnsupportedMessageException, UnknownIdentifierException {
        TruffleString tsName = Strings.fromJavaString(fromJavaStringNode, name);
        Object curValue = Properties.getOrDefault(access, scope, tsName, null);
        if (curValue == null || curValue == Dead.instance()) {
            throw UnknownIdentifierException.create(name);
        } else if (!isConst(tsName, access)) {
            Properties.putIfPresent(access, scope, tsName, value);
        } else {
            throw UnsupportedMessageException.create();
        }
    }
}
