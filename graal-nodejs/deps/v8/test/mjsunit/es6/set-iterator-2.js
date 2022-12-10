// Copyright 2018 the V8 project authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// Flags: --allow-natives-syntax --no-stress-opt

var set = new Set([1,2,3]);

assertTrue(%SetIteratorProtector());
assertTrue(%MapIteratorProtector());

// This changes %SetPrototype%. No more tests should be run after this in the
// same instance.
set.__proto__[Symbol.iterator] = () => ({next: () => ({done: true})});

assertFalse(%SetIteratorProtector());
assertTrue(%MapIteratorProtector());
assertEquals([], [...set]);
assertEquals([[1,1],[2,2],[3,3]], [...set.entries()]);
assertEquals([1,2,3], [...set.keys()]);
assertEquals([1,2,3], [...set.values()]);
