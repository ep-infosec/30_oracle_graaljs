/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
 */

/**
 * Tests a potential clash/re-definition of 'quit' global built-in.
 * 
 * @option nashorn-compat
 * @option shell
 */

quit();
throw new Error("unreachable");
