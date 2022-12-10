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
package com.oracle.truffle.js.runtime.objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.builtins.JSBuiltinsContainer;
import com.oracle.truffle.js.runtime.JSConfig;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.Properties;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.Symbol;
import com.oracle.truffle.js.runtime.builtins.Builtin;
import com.oracle.truffle.js.runtime.builtins.JSClass;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.builtins.JSFunctionData;
import com.oracle.truffle.js.runtime.builtins.JSOrdinary;

/**
 * @see JSDynamicObject
 */
public final class JSObjectUtil {

    private static final HiddenKey PROTOTYPE_DATA = new HiddenKey("PROTOTYPE_DATA");

    private JSObjectUtil() {
        // this utility class should not be instantiated
    }

    /**
     * Formats {@link JSDynamicObject#defaultToString()}, by default returns "[object ...]".
     *
     * @param object object to be used
     * @return "[object ...]" by default
     */
    @TruffleBoundary
    public static TruffleString formatToString(TruffleString object) {
        return Strings.concatAll(Strings.BRACKET_OBJECT_SPC, object, Strings.BRACKET_CLOSE);
    }

    public static JSObject createOrdinaryPrototypeObject(JSRealm realm) {
        CompilerAsserts.neverPartOfCompilation();
        return createOrdinaryPrototypeObject(realm, realm.getObjectPrototype());
    }

    public static JSObject createOrdinaryPrototypeObject(JSRealm realm, JSDynamicObject prototype) {
        CompilerAsserts.neverPartOfCompilation();
        // slow; only use for initialization
        assert prototype == Null.instance || JSRuntime.isObject(prototype);

        JSContext context = realm.getContext();
        JSObject obj;
        if (context.isMultiContext()) {
            obj = JSOrdinary.createInitWithInstancePrototype(prototype, context);
        } else {
            Shape initialShape = prototype == Null.instance ? context.getEmptyShapeNullPrototype() : JSObjectUtil.getProtoChildShape(prototype, JSOrdinary.INSTANCE, context);
            obj = JSOrdinaryObject.create(initialShape);
        }
        return obj;
    }

    public static void setOrVerifyPrototype(JSContext context, JSDynamicObject obj, JSDynamicObject prototype) {
        CompilerAsserts.neverPartOfCompilation();
        assert prototype == Null.instance || JSRuntime.isObject(prototype);
        if (context.isMultiContext()) {
            JSObjectUtil.putHiddenProperty(obj, JSObject.HIDDEN_PROTO, prototype);
        } else {
            assert JSObjectUtil.getHiddenProperty(obj, JSObject.HIDDEN_PROTO) == prototype;
        }
    }

    public static boolean isValidPrototype(Object proto) {
        return proto == Null.instance || JSRuntime.isObject(proto);
    }

    @TruffleBoundary
    public static void putDataProperty(JSContext context, JSDynamicObject thisObj, Object key, Object value, int flags) {
        assert checkForExistingProperty(thisObj, key);
        defineDataProperty(context, thisObj, key, value, flags);
    }

    @TruffleBoundary
    public static void putDataProperty(JSDynamicObject thisObj, Object name, Object value, int flags) {
        JSContext context = JSObject.getJSContext(thisObj);
        putDataProperty(context, thisObj, name, value, flags);
    }

    @TruffleBoundary
    public static void defineDataProperty(JSContext context, JSDynamicObject thisObj, Object key, Object value, int flags) {
        checkForNoSuchPropertyOrMethod(context, key);
        Properties.putWithFlagsUncached(thisObj, key, value, flags);
    }

    @TruffleBoundary
    public static void defineDataProperty(JSDynamicObject thisObj, Object key, Object value, int flags) {
        JSContext context = JSObject.getJSContext(thisObj);
        defineDataProperty(context, thisObj, key, value, flags);
    }

    public static void putOrSetDataProperty(JSContext context, JSDynamicObject thisObj, Object key, Object value, int flags) {
        if (!JSObject.hasOwnProperty(thisObj, key)) {
            JSObjectUtil.putDataProperty(context, thisObj, key, value, flags);
        } else {
            JSObject.set(thisObj, key, value);
        }
    }

    @TruffleBoundary
    public static void defineAccessorProperty(JSDynamicObject thisObj, Object key, Accessor accessor, int flags) {
        int finalFlags = flags | JSProperty.ACCESSOR;

        JSContext context = JSObject.getJSContext(thisObj);
        checkForNoSuchPropertyOrMethod(context, key);
        Properties.putWithFlagsUncached(thisObj, key, accessor, finalFlags);
    }

    @TruffleBoundary
    public static void defineProxyProperty(JSDynamicObject thisObj, Object key, PropertyProxy proxy, int flags) {
        int finalFlags = flags | JSProperty.PROXY;

        JSContext context = JSObject.getJSContext(thisObj);
        checkForNoSuchPropertyOrMethod(context, key);
        Properties.putConstantUncached(thisObj, key, proxy, finalFlags);
    }

    @TruffleBoundary
    public static void changePropertyFlags(JSDynamicObject thisObj, Object key, int flags) {
        // only javascript flags allowed here
        assert flags == (flags & JSAttributes.ATTRIBUTES_MASK);

        JSDynamicObject.updatePropertyFlags(thisObj, key, (attr) -> (attr & ~JSAttributes.ATTRIBUTES_MASK) | flags);
    }

    public static void putDataProperty(JSContext context, JSDynamicObject thisObj, Object name, Object value) {
        putDataProperty(context, thisObj, name, value, JSAttributes.notConfigurableNotEnumerableNotWritable());
    }

    @TruffleBoundary
    public static void putDeclaredDataProperty(JSContext context, JSDynamicObject thisObj, Object key, Object value, int flags) {
        assert JSRuntime.isPropertyKey(key);
        assert checkForExistingProperty(thisObj, key);

        checkForNoSuchPropertyOrMethod(context, key);
        Properties.putConstantUncached(thisObj, key, value, flags);
    }

    public static void putConstructorProperty(JSContext context, JSDynamicObject prototype, JSDynamicObject constructor) {
        putDataProperty(context, prototype, JSObject.CONSTRUCTOR, constructor, JSAttributes.configurableNotEnumerableWritable());
    }

    public static void putConstructorPrototypeProperty(JSContext ctx, JSDynamicObject constructor, JSDynamicObject prototype) {
        putDataProperty(ctx, constructor, JSObject.PROTOTYPE, prototype, JSAttributes.notConfigurableNotEnumerableNotWritable());
    }

    public static void putToStringTag(JSDynamicObject prototype, TruffleString toStringTag) {
        assert checkForExistingProperty(prototype, Symbol.SYMBOL_TO_STRING_TAG);
        Properties.putWithFlagsUncached(prototype, Symbol.SYMBOL_TO_STRING_TAG, toStringTag, JSAttributes.configurableNotEnumerableNotWritable());
    }

    @TruffleBoundary
    public static void putAccessorProperty(JSContext context, JSDynamicObject thisObj, Object key, JSDynamicObject getter, JSDynamicObject setter, int flags) {
        Accessor accessor = new Accessor(getter, setter);
        putAccessorProperty(context, thisObj, key, accessor, flags);
    }

    @TruffleBoundary
    public static void putAccessorProperty(JSContext context, JSDynamicObject thisObj, Object key, Accessor accessor, int flags) {
        assert JSRuntime.isPropertyKey(key);
        assert checkForExistingProperty(thisObj, key);

        checkForNoSuchPropertyOrMethod(context, key);
        Properties.putWithFlagsUncached(thisObj, key, accessor, flags | JSProperty.ACCESSOR);
    }

    public static void putBuiltinAccessorProperty(JSDynamicObject thisObj, Object key, JSDynamicObject getter, JSDynamicObject setter) {
        putBuiltinAccessorProperty(thisObj, key, getter, setter, JSAttributes.configurableNotEnumerable());
    }

    @TruffleBoundary
    public static void putBuiltinAccessorProperty(JSDynamicObject thisObj, Object key, JSDynamicObject getter, JSDynamicObject setter, int flags) {
        Accessor accessor = new Accessor(getter, setter);
        putBuiltinAccessorProperty(thisObj, key, accessor, flags);
    }

    @TruffleBoundary
    public static void putBuiltinAccessorProperty(JSDynamicObject thisObj, Object key, Accessor accessor, int flags) {
        assert JSRuntime.isPropertyKey(key) && !isNoSuchPropertyOrMethod(key);
        assert checkForExistingProperty(thisObj, key);
        Properties.putWithFlagsUncached(thisObj, key, accessor, flags | JSProperty.ACCESSOR);
    }

    public static void putBuiltinAccessorProperty(JSDynamicObject thisObj, Object key, Accessor accessor) {
        putBuiltinAccessorProperty(thisObj, key, accessor, JSAttributes.configurableNotEnumerable());
    }

    public static void putProxyProperty(JSDynamicObject thisObj, Object key, PropertyProxy proxy, int flags) {
        assert JSRuntime.isPropertyKey(key) && !isNoSuchPropertyOrMethod(key);
        assert checkForExistingProperty(thisObj, key);
        defineProxyProperty(thisObj, key, proxy, flags);
    }

    private static boolean checkForExistingProperty(JSDynamicObject thisObj, Object key) {
        assert !thisObj.getShape().hasProperty(key) : "Don't put a property that already exists. Use the setters.";
        return true;
    }

    /**
     * Get or create a prototype child shape inheriting from this object, migrating the object to a
     * unique shape in the process. Creating unique shapes should be avoided in the fast path.
     */
    public static Shape getProtoChildShape(JSDynamicObject obj, JSClass jsclass, JSContext context) {
        CompilerAsserts.neverPartOfCompilation();
        if (obj == null) {
            return context.makeEmptyShapeWithPrototypeInObject(jsclass);
        }
        assert JSRuntime.isObject(obj);
        Shape protoChild = getProtoChildShapeMaybe(obj, jsclass);
        if (protoChild != null) {
            return protoChild;
        }

        return getProtoChildShapeSlowPath(obj, jsclass, context);
    }

    public static Shape getProtoChildShape(JSDynamicObject obj, JSClass jsclass, JSContext context, BranchProfile branchProfile) {
        Shape protoChild = getProtoChildShapeMaybe(obj, jsclass);
        if (protoChild != null) {
            return protoChild;
        }

        branchProfile.enter();
        return getProtoChildShapeSlowPath(obj, jsclass, context);
    }

    private static Shape getProtoChildShapeMaybe(JSDynamicObject obj, JSClass jsclass) {
        Shape protoChild = JSShape.getProtoChildTree(obj, jsclass);
        assert protoChild == null || JSShape.getJSClassNoCast(protoChild) == jsclass;
        return protoChild;
    }

    @TruffleBoundary
    private static Shape getProtoChildShapeSlowPath(JSDynamicObject obj, JSClass jsclass, JSContext context) {
        JSPrototypeData prototypeData = getPrototypeData(obj);
        if (prototypeData == null) {
            prototypeData = putPrototypeData(obj);
        }
        return prototypeData.getOrAddProtoChildTree(jsclass, createChildRootShape(obj, jsclass, context));
    }

    private static Shape createChildRootShape(JSDynamicObject proto, JSClass jsclass, JSContext context) {
        CompilerAsserts.neverPartOfCompilation();
        assert proto != null && proto != Null.instance;
        return JSShape.createObjectShape(context, jsclass, proto);
    }

    public static JSPrototypeData putPrototypeData(JSDynamicObject obj) {
        CompilerAsserts.neverPartOfCompilation();
        assert getPrototypeData(obj) == null;
        JSPrototypeData prototypeData = new JSPrototypeData();
        putPrototypeData(obj, prototypeData);
        return prototypeData;
    }

    private static void putPrototypeData(JSDynamicObject obj, JSPrototypeData prototypeData) {
        boolean extensible = JSShape.isExtensible(obj.getShape());
        JSObjectUtil.putHiddenProperty(obj, PROTOTYPE_DATA, prototypeData);
        assert extensible == JSShape.isExtensible(obj.getShape());
    }

    static JSPrototypeData getPrototypeData(JSDynamicObject obj) {
        return (JSPrototypeData) JSDynamicObject.getOrNull(obj, PROTOTYPE_DATA);
    }

    public static Map<Object, Object> archive(JSDynamicObject obj) {
        HashMap<Object, Object> ret = new HashMap<>();
        Shape shape = obj.getShape();
        for (Property prop : shape.getPropertyListInternal(false)) {
            if (!prop.getLocation().isConstant() && !ret.containsKey(prop.getKey())) {
                ret.put(prop.getKey(), JSDynamicObject.getOrNull(obj, prop.getKey()));
            }
        }
        return ret;
    }

    @TruffleBoundary
    public static void setPrototypeImpl(JSDynamicObject object, JSDynamicObject newPrototype) {
        CompilerAsserts.neverPartOfCompilation();
        assert JSShape.isPrototypeInShape(object.getShape());

        final JSContext context = JSObject.getJSContext(object);
        final Shape oldShape = object.getShape();
        JSShape.invalidatePrototypeAssumption(oldShape);
        final Shape newRootShape;
        JSClass jsclass = JSShape.getJSClass(oldShape);
        if (newPrototype == Null.instance) {
            newRootShape = context.makeEmptyShapeWithNullPrototype(jsclass);
        } else {
            assert JSRuntime.isObject(newPrototype) : newPrototype;
            if (context.isMultiContext()) {
                newRootShape = context.makeEmptyShapeWithPrototypeInObject(jsclass);
            } else {
                newRootShape = JSObjectUtil.getProtoChildShape(newPrototype, jsclass, context);
            }
        }

        DynamicObjectLibrary lib = DynamicObjectLibrary.getUncached();

        List<Property> allProperties = oldShape.getPropertyListInternal(true);
        List<Object> archive = new ArrayList<>(allProperties.size());
        for (Property prop : allProperties) {
            Object value = Properties.getOrDefault(lib, object, prop.getKey(), null);
            archive.add(value);
        }

        lib.resetShape(object, newRootShape);

        for (int i = 0; i < allProperties.size(); i++) {
            Property property = allProperties.get(i);
            Object key = property.getKey();
            if (!newRootShape.hasProperty(key)) {
                Object value = archive.get(i);
                int propertyFlags = property.getFlags();
                if (JSObject.HIDDEN_PROTO.equals(key)) {
                    // Note possible transition from constant shape prototype to instance property
                    Properties.putWithFlags(lib, object, key, newPrototype, propertyFlags);
                } else if (property.getLocation().isConstant()) {
                    Properties.putConstant(lib, object, key, value, propertyFlags);
                } else {
                    Properties.putWithFlags(lib, object, key, value, propertyFlags);
                }
            }
        }

        assert JSObjectUtil.getPrototype(object) == newPrototype;
    }

    public static JSDynamicObject getPrototype(JSDynamicObject thisObj) {
        JSSharedData sharedData = JSShape.getSharedData(thisObj.getShape());
        JSDynamicObject proto = sharedData.getPrototype();
        if (proto != null) {
            assert proto == JSDynamicObject.getOrDefault(thisObj, JSObject.HIDDEN_PROTO, Null.instance);
            return proto;
        }
        return (JSDynamicObject) JSDynamicObject.getOrDefault(thisObj, JSObject.HIDDEN_PROTO, Null.instance);
    }

    public static <T> T checkForNoSuchPropertyOrMethod(JSContext context, T key) {
        CompilerAsserts.neverPartOfCompilation();
        if (context != null && key != null && context.isOptionNashornCompatibilityMode()) {
            if (context.getNoSuchPropertyUnusedAssumption().isValid() && JSObject.NO_SUCH_PROPERTY_NAME.equals(key)) {
                context.getNoSuchPropertyUnusedAssumption().invalidate("NoSuchProperty is used");
            }
            if (context.getNoSuchMethodUnusedAssumption().isValid() && JSObject.NO_SUCH_METHOD_NAME.equals(key)) {
                context.getNoSuchMethodUnusedAssumption().invalidate("NoSuchMethod is used");
            }
        }
        return key;
    }

    public static boolean isNoSuchPropertyOrMethod(Object key) {
        CompilerAsserts.neverPartOfCompilation();
        return (Strings.isTString(key) && (Strings.equals(JSObject.NO_SUCH_PROPERTY_NAME, (TruffleString) key) || Strings.equals(JSObject.NO_SUCH_METHOD_NAME, (TruffleString) key)));
    }

    public static JSDynamicObject createSymbolSpeciesGetterFunction(JSRealm realm) {
        return JSFunction.create(realm, realm.getContext().getSymbolSpeciesThisGetterFunctionData());
    }

    public static void putFunctionsFromContainer(JSRealm realm, JSDynamicObject thisObj, JSBuiltinsContainer container) {
        JSContext context = realm.getContext();
        container.forEachBuiltin(new Consumer<Builtin>() {
            @Override
            public void accept(Builtin builtin) {
                if (!builtin.isIncluded(context)) {
                    return;
                } else if (builtin.isGetter() || builtin.isSetter()) {
                    return;
                }
                JSFunctionData functionData = builtin.createFunctionData(context);
                putDataProperty(context, thisObj, builtin.getKey(), JSFunction.create(realm, functionData), builtin.getAttributeFlags());
            }
        });
    }

    public static void putHiddenProperty(JSDynamicObject obj, Object key, Object value) {
        assert key instanceof HiddenKey;
        Properties.putUncached(obj, key, value);
    }

    public static Object getHiddenProperty(JSDynamicObject obj, Object key) {
        assert key instanceof HiddenKey;
        return Properties.getOrDefaultUncached(obj, key, null);
    }

    public static boolean hasHiddenProperty(JSDynamicObject obj, Object key) {
        assert key instanceof HiddenKey;
        return Properties.containsKeyUncached(obj, key);
    }

    public static DynamicObjectLibrary createCached(Object key, JSDynamicObject obj) {
        assert key != null;
        return DynamicObjectLibrary.getFactory().create(obj);
    }

    public static DynamicObjectLibrary createDispatched(Object key, int limit) {
        assert key != null;
        return DynamicObjectLibrary.getFactory().createDispatched(limit);
    }

    public static DynamicObjectLibrary createDispatched(Object key) {
        return createDispatched(key, JSConfig.PropertyCacheLimit);
    }

    public static <T extends JSDynamicObject> T copyProperties(T target, JSDynamicObject source) {
        DynamicObjectLibrary objectLibrary = DynamicObjectLibrary.getUncached();
        for (Property property : source.getShape().getPropertyListInternal(true)) {
            Object key = property.getKey();
            if (Properties.containsKey(objectLibrary, target, key)) {
                continue;
            }
            Object value = Properties.getOrDefault(objectLibrary, source, key, null);
            if (property.getLocation().isConstant()) {
                Properties.putConstant(objectLibrary, target, key, value, property.getFlags());
            } else {
                Properties.putWithFlags(objectLibrary, target, key, value, property.getFlags());
            }
        }
        return target;
    }
}
