/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
 */

/**
 * WebAssembly.Table.prototype.get should return an exported function
 * 
 * @option webassembly
 */

load('../js/assert.js');

// (module
//   (table $defaultTable (export "defaultTable") 4 anyfunc)
//   (func $square (param i32) (result i32)
//     get_local 0
//     get_local 0
//     i32.mul
//   )
//   (elem (i32.const 0) $square)
// )
var bytes = [0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00, 0x01, 0x06, 0x01, 0x60, 0x01,
    0x7f, 0x01, 0x7f, 0x03, 0x02, 0x01, 0x00, 0x04, 0x04, 0x01, 0x70, 0x00, 0x04,
    0x07, 0x10, 0x01, 0x0c, 0x64, 0x65, 0x66, 0x61, 0x75, 0x6c, 0x74, 0x54, 0x61,
    0x62, 0x6c, 0x65, 0x01, 0x00, 0x09, 0x07, 0x01, 0x00, 0x41, 0x00, 0x0b, 0x01,
    0x00, 0x0a, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x00, 0x6c, 0x0b, 0x00,
    0x17, 0x04, 0x6e, 0x61, 0x6d, 0x65, 0x01, 0x09, 0x01, 0x00, 0x06, 0x73, 0x71,
    0x75, 0x61, 0x72, 0x65, 0x02, 0x05, 0x01, 0x00, 0x01, 0x00, 0x00]
var module = new WebAssembly.Module(new Uint8Array(bytes));
var instance = new WebAssembly.Instance(module);
var square = instance.exports.defaultTable.get(0);

assertSame('function', typeof square);
assertTrue(square instanceof Function);
assertSame(81, square(9));
assertSame(100, square.apply(undefined, [10]));
assertSame(121, square.call(undefined, 11));
