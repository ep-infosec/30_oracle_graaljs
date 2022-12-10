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
package com.oracle.truffle.js.builtins.helper;

import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.Strings;

public final class FloatParser {

    private final TruffleString input;
    private int pos;
    private boolean isNaN;
    private final double value;

    public FloatParser(TruffleString s, FloatParserNode node) {
        input = s;
        pos = 0;
        isNaN = false;
        value = parse(node);
    }

    public double getResult() {
        return value;
    }

    private double parse(FloatParserNode node) {
        strDecimalLiteral(node);
        if (isNaN) {
            return Double.NaN;
        }
        return parseValidSubstring(node);
    }

    private double parseValidSubstring(FloatParserNode node) {
        // always use lazy substring here, since the substring never escapes
        TruffleString validSubstring = Strings.substring(true, node.substringNode, input, 0, pos);
        try {
            return Strings.parseDouble(node.parseDoubleNode, validSubstring);
        } catch (TruffleString.NumberFormatException e) {
            isNaN = true;
            return Double.NaN;
        }
    }

    private void strDecimalLiteral(FloatParserNode node) {
        char currentChar = current(node);
        if (currentChar == '+' || currentChar == '-') {
            next();
            currentChar = current(node);
        }
        if (JSRuntime.isAsciiDigit(currentChar) || currentChar == '.') {
            strUnsignedDecimalLiteral(node);
        } else {
            isNaN = true;
        }
    }

    private void strUnsignedDecimalLiteral(FloatParserNode node) {
        if (JSRuntime.isAsciiDigit(current(node))) {
            decimalDigits(node);
        }
        int prevPos = pos;
        if (hasNext() && current(node) == '.') {
            next();
            if (JSRuntime.isAsciiDigit(current(node))) {
                decimalDigits(node);
            }
        }
        if (isNaN) {
            pos = prevPos;
            isNaN = false;
            return;
        }
        prevPos = pos;
        if (isExponentPart(node)) {
            exponentPart(node);
        }
        if (isNaN) {
            pos = prevPos;
            isNaN = false;
        }
    }

    private void next() {
        pos++;
    }

    private char current(FloatParserNode node) {
        if (hasNext()) {
            return Strings.charAt(node.charAtNode, input, pos);
        } else {
            return 0;
        }
    }

    private boolean hasNext() {
        return pos < Strings.length(input);
    }

    private void exponentPart(FloatParserNode node) {
        node.exponentBranch.enter();
        assert current(node) == 'e' || current(node) == 'E';
        next();
        char currentChar = current(node);
        if (JSRuntime.isAsciiDigit(currentChar)) {
            decimalDigits(node);
        } else if (currentChar == '+' || currentChar == '-') {
            next();
            decimalDigits(node);
        } else {
            isNaN = true;
        }
    }

    private boolean isExponentPart(FloatParserNode node) {
        if (hasNext()) {
            char firstChar = current(node);
            return firstChar == 'e' || firstChar == 'E';
        }
        return false;
    }

    private void decimalDigits(FloatParserNode node) {
        char currentChar = current(node);
        boolean valid = false;
        while (JSRuntime.isAsciiDigit(currentChar) && hasNext()) {
            valid = true;
            next();
            currentChar = current(node);
        }
        if (!valid) {
            isNaN = true;
        }
    }
}
