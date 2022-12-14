/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
 */

// Just ensure that no (internal) error is thrown
new Intl.DateTimeFormat('en', { month: 'numeric', hour: 'numeric' }).format(new Date());
