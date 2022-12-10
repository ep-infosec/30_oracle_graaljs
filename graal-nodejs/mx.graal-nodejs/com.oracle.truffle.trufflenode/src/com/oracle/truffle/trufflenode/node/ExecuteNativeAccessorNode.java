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
package com.oracle.truffle.trufflenode.node;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.js.nodes.access.GetPrototypeNode;
import com.oracle.truffle.js.nodes.access.PropertyGetNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSFrameUtil;
import com.oracle.truffle.js.runtime.JavaScriptRootNode;
import com.oracle.truffle.js.runtime.builtins.JSFunctionObject;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.trufflenode.GraalJSAccess;
import com.oracle.truffle.trufflenode.NativeAccess;
import com.oracle.truffle.trufflenode.info.Accessor;
import com.oracle.truffle.trufflenode.info.FunctionTemplate;

/**
 *
 * @author Jan Stola
 */
public class ExecuteNativeAccessorNode extends JavaScriptRootNode {

    private final boolean getter;
    private final BranchProfile errorBranch = BranchProfile.create();

    @Child private GetPrototypeNode getPrototypeNode;
    @Child private PropertyGetNode prototypePropertyGetNode;
    @Child private PropertyGetNode holderPropertyGetNode;
    @Child private PropertyGetNode accessorGetNode;

    public ExecuteNativeAccessorNode(JSContext context, boolean getter) {
        this.getter = getter;
        this.getPrototypeNode = GetPrototypeNode.create();
        this.prototypePropertyGetNode = PropertyGetNode.create(JSObject.PROTOTYPE, false, context);
        this.accessorGetNode = PropertyGetNode.createGetHidden(GraalJSAccess.ACCESSOR_KEY, context);
        this.holderPropertyGetNode = PropertyGetNode.createGetHidden(GraalJSAccess.HOLDER_KEY, context);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object[] arguments = frame.getArguments();
        Accessor accessor = (Accessor) accessorGetNode.getValue(JSFrameUtil.getFunctionObject(frame));

        if (accessor.hasSignature()) {
            FunctionTemplate signature = accessor.getSignature();
            JSFunctionObject functionObject = signature.getFunctionObject(getRealm());
            if (functionObject != null) {
                Object functionPrototype = prototypePropertyGetNode.getValue(functionObject);
                Object instancePrototype = getPrototypeNode.execute(arguments[0]);
                if (functionPrototype != instancePrototype) {
                    errorBranch.enter();
                    throw Errors.createTypeError(incompatibleReceiverMessage(accessor));
                }
            }
        }
        long functionPointer = getter ? accessor.getGetterPtr() : accessor.getSetterPtr();
        if (functionPointer == 0) {
            assert !getter;
            return false;
        }
        Object holder = holderPropertyGetNode.getValue(arguments[1]);
        return executeAccessorMethod(functionPointer, accessor, holder, arguments);
    }

    @CompilerDirectives.TruffleBoundary
    private static String incompatibleReceiverMessage(Accessor accessor) {
        return "Method " + accessor.getName() + " called on incompatible receiver";
    }

    @CompilerDirectives.TruffleBoundary
    private Object executeAccessorMethod(long functionPointer, Accessor accessor, Object holder, Object[] arguments) {
        Object result;
        if (getter) {
            result = NativeAccess.executeAccessorGetter(functionPointer, holder, accessor.getName(), arguments, accessor.getData());
            GraalJSAccess graalAccess = GraalJSAccess.get(this);
            result = graalAccess.correctReturnValue(result);
        } else {
            NativeAccess.executeAccessorSetter(functionPointer, holder, accessor.getName(), arguments, accessor.getData());
            result = Undefined.instance;
        }
        return result;
    }

    @Override
    public String toString() {
        return "NativeAccessor";
    }
}
