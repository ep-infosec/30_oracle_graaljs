/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.nodes.binary;

import java.util.Set;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.nodes.JSGuards;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.access.JSConstantNode.JSConstantBooleanNode;
import com.oracle.truffle.js.nodes.access.JSConstantNode.JSConstantIntegerNode;
import com.oracle.truffle.js.nodes.access.JSConstantNode.JSConstantNullNode;
import com.oracle.truffle.js.nodes.access.JSConstantNode.JSConstantStringNode;
import com.oracle.truffle.js.nodes.access.JSConstantNode.JSConstantUndefinedNode;
import com.oracle.truffle.js.nodes.unary.IsIdenticalBooleanNode;
import com.oracle.truffle.js.nodes.unary.IsIdenticalIntegerNode;
import com.oracle.truffle.js.nodes.unary.IsIdenticalStringNode;
import com.oracle.truffle.js.nodes.unary.IsIdenticalUndefinedNode;
import com.oracle.truffle.js.nodes.unary.IsNullNode;
import com.oracle.truffle.js.runtime.BigInt;
import com.oracle.truffle.js.runtime.JSConfig;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.Symbol;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.Undefined;

/**
 * IsStrictlyEqual(x, y) aka {@code ===} operator.
 *
 * @see JSEqualNode
 */
@NodeInfo(shortName = "===")
@ImportStatic({JSRuntime.class, JSConfig.class, JSGuards.class})
public abstract class JSIdenticalNode extends JSCompareNode {
    protected static final int MAX_CLASSES = 3;

    protected static final int STRICT_EQUALITY_COMPARISON = 0;
    protected static final int SAME_VALUE = 1;
    protected static final int SAME_VALUE_ZERO = 2;

    protected final int type;

    protected JSIdenticalNode(JavaScriptNode left, JavaScriptNode right, int type) {
        super(left, right);
        this.type = type;
    }

    public static JSIdenticalNode createStrictEqualityComparison() {
        return JSIdenticalNodeGen.create(null, null, STRICT_EQUALITY_COMPARISON);
    }

    public static JSIdenticalNode createSameValue() {
        return JSIdenticalNodeGen.create(null, null, SAME_VALUE);
    }

    public static JSIdenticalNode createSameValue(JavaScriptNode left, JavaScriptNode right) {
        return JSIdenticalNodeGen.create(left, right, SAME_VALUE);
    }

    public static JSIdenticalNode createSameValueZero() {
        return JSIdenticalNodeGen.create(null, null, SAME_VALUE_ZERO);
    }

    public static JavaScriptNode createUnoptimized(JavaScriptNode left, JavaScriptNode right) {
        return JSIdenticalNodeGen.create(left, right, STRICT_EQUALITY_COMPARISON);
    }

    public static JavaScriptNode create(JavaScriptNode left, JavaScriptNode right) {
        if (left instanceof JSConstantNullNode) {
            return IsNullNode.create(right, true);
        } else if (right instanceof JSConstantNullNode) {
            return IsNullNode.create(left, false);
        } else if (left instanceof JSConstantStringNode) {
            return IsIdenticalStringNode.create((TruffleString) left.execute(null), right, true);
        } else if (right instanceof JSConstantStringNode) {
            return IsIdenticalStringNode.create((TruffleString) right.execute(null), left, false);
        } else if (left instanceof JSConstantIntegerNode) {
            return IsIdenticalIntegerNode.create((int) left.execute(null), right, true);
        } else if (right instanceof JSConstantIntegerNode) {
            return IsIdenticalIntegerNode.create((int) right.execute(null), left, false);
        } else if (left instanceof JSConstantBooleanNode) {
            return IsIdenticalBooleanNode.create((boolean) left.execute(null), right, true);
        } else if (right instanceof JSConstantBooleanNode) {
            return IsIdenticalBooleanNode.create((boolean) right.execute(null), left, false);
        } else if (left instanceof JSConstantUndefinedNode) {
            return IsIdenticalUndefinedNode.create(right, true);
        } else if (right instanceof JSConstantUndefinedNode) {
            return IsIdenticalUndefinedNode.create(left, false);
        }
        return JSIdenticalNodeGen.create(left, right, STRICT_EQUALITY_COMPARISON);
    }

    public abstract boolean executeBoolean(Object left, Object right);

    @Specialization
    protected static boolean doInt(int a, int b) {
        return a == b;
    }

    @Specialization
    protected boolean doDouble(double a, double b) {
        if (type == STRICT_EQUALITY_COMPARISON) {
            return a == b;
        } else if (type == SAME_VALUE) {
            if (Double.isNaN(a)) {
                return Double.isNaN(b);
            }
            if (a == 0 && b == 0) {
                return JSRuntime.isNegativeZero(a) == JSRuntime.isNegativeZero(b);
            }
            return a == b;
        } else {
            assert type == SAME_VALUE_ZERO;
            if (Double.isNaN(a)) {
                return Double.isNaN(b);
            }
            return a == b;
        }
    }

    @Specialization
    protected static boolean doBoolean(boolean a, boolean b) {
        return a == b;
    }

    @Specialization
    protected static boolean doBigInt(BigInt a, BigInt b) {
        return a.compareTo(b) == 0;
    }

    @Specialization
    protected static boolean doBigIntDouble(@SuppressWarnings("unused") BigInt a, @SuppressWarnings("unused") double b) {
        return false;
    }

    @Specialization
    protected static boolean doDoubleBigInt(double a, BigInt b) {
        return doBigIntDouble(b, a);
    }

    @Specialization(guards = {"isUndefined(a)"})
    protected static boolean doUndefinedA(Object a, Object b) {
        return a == b;
    }

    @Specialization(guards = {"isUndefined(b)"})
    protected static boolean doUndefinedB(Object a, Object b) {
        return a == b;
    }

    @Specialization
    protected static boolean doJSObjectA(JSObject a, Object b) {
        return a == b;
    }

    @Specialization
    protected static boolean doJSObjectB(Object a, JSObject b) {
        return a == b;
    }

    @Specialization(guards = {"isJSNull(a)", "isJSNull(b)"})
    protected static boolean doNullNull(@SuppressWarnings("unused") Object a, @SuppressWarnings("unused") Object b) {
        return true;
    }

    @Specialization(guards = {"isJSNull(a)", "isUndefined(b)"})
    protected static boolean doNullUndefined(@SuppressWarnings("unused") Object a, @SuppressWarnings("unused") Object b) {
        return false;
    }

    @Specialization(guards = {"isUndefined(a)", "isJSNull(b)"})
    protected static boolean doUndefinedNull(@SuppressWarnings("unused") Object a, @SuppressWarnings("unused") Object b) {
        return false;
    }

    @Specialization(guards = {"isJSNull(a)", "!isNullOrUndefined(b)"})
    protected static boolean doNullA(@SuppressWarnings("unused") Object a, Object b,
                    @Shared("isNullInterop") @CachedLibrary(limit = "InteropLibraryLimit") InteropLibrary nullInterop) {
        assert b != Undefined.instance;
        return nullInterop.isNull(b);
    }

    @Specialization(guards = {"!isNullOrUndefined(a)", "isJSNull(b)"})
    protected static boolean doNullB(Object a, @SuppressWarnings("unused") Object b,
                    @Shared("isNullInterop") @CachedLibrary(limit = "InteropLibraryLimit") InteropLibrary nullInterop) {
        assert a != Undefined.instance;
        return nullInterop.isNull(a);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isReferenceEquals(a, b)")
    protected static boolean doTruffleStringIdentity(TruffleString a, TruffleString b) {
        return true;
    }

    @Specialization(replaces = "doTruffleStringIdentity")
    protected static boolean doTruffleString(TruffleString a, TruffleString b,
                    @Cached TruffleString.EqualNode equalsNode) {
        return equalsNode.execute(a, b, TruffleString.Encoding.UTF_16);
    }

    @Specialization
    protected static boolean doSymbol(Symbol a, Symbol b) {
        return a == b;
    }

    @Specialization(guards = {"isBoolean(a) != isBoolean(b)"})
    protected static boolean doBooleanNotBoolean(Object a, Object b) {
        assert (a != null) && (b != null);
        return false;
    }

    @Specialization(guards = {"isSymbol(a) != isSymbol(b)"})
    protected static boolean doSymbolNotSymbol(Object a, Object b) {
        assert (a != null) && (b != null);
        return false;
    }

    protected static boolean isJavaNumberType(Class<?> clazz) {
        return Number.class.isAssignableFrom(clazz);
    }

    /**
     * lhs and rhs are of different types. This specialization is used only for type classes that
     * are wider than a single type (to be more specific, only numbers, currently).
     */
    @SuppressWarnings("unused")
    @Specialization(guards = {"a.getClass() == cachedClassA", "b.getClass() == cachedClassB",
                    "isJavaNumberType(cachedClassA) != isJavaNumberType(cachedClassB)"}, limit = "MAX_CLASSES")
    protected static boolean doNumberNotNumberCached(Object a, Object b, //
                    @Cached("a.getClass()") Class<?> cachedClassA, //
                    @Cached("b.getClass()") Class<?> cachedClassB) {
        return false;
    }

    @Specialization(guards = {"isJavaNumber(a) != isJavaNumber(b)"}, replaces = "doNumberNotNumberCached")
    protected static boolean doNumberNotNumber(Object a, Object b) {
        assert (a != null) && (b != null);
        return false;
    }

    @Specialization(guards = {"isString(a) != isString(b)"})
    protected static boolean doStringNotString(Object a, Object b) {
        assert (a != null) && (b != null);
        return false;
    }

    @Specialization(guards = {"isJavaNumber(a)", "isJavaNumber(b)"})
    protected boolean doNumber(Number a, Number b) {
        return doDouble(JSRuntime.doubleValue(a), JSRuntime.doubleValue(b));
    }

    @Specialization(guards = {"isForeignObject(a)", "isForeignObject(b)"}, limit = "InteropLibraryLimit")
    protected static boolean doForeignObject(Object a, Object b,
                    @CachedLibrary("a") InteropLibrary aInterop,
                    @CachedLibrary("b") InteropLibrary bInterop) {
        return aInterop.isIdentical(a, b, bInterop) || (aInterop.isNull(a) && bInterop.isNull(b));
    }

    @Fallback
    protected static boolean doFallback(Object a, Object b) {
        assert !JSRuntime.identical(a, b) : a + " (" + (a == null ? "null" : a.getClass()) + ")" + ", " + b + " (" + (b == null ? "null" : b.getClass()) + ")";
        return false;
    }

    @Override
    protected JavaScriptNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
        return JSIdenticalNodeGen.create(cloneUninitialized(getLeft(), materializedTags), cloneUninitialized(getRight(), materializedTags), type);
    }
}
