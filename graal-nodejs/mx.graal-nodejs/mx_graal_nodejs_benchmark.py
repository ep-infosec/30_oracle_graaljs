#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#
# ----------------------------------------------------------------------------------------------------

import mx, mx_benchmark, mx_graal_nodejs
from mx_benchmark import GuestVm

class GraalNodeJsVm(GuestVm):
    def __init__(self, config_name, options, host_vm=None):
        super(GraalNodeJsVm, self).__init__(host_vm=host_vm)
        self._config_name = config_name
        self._options = options

    def name(self):
        return 'graal-js'

    def config_name(self):
        return self._config_name

    def hosting_registry(self):
        return mx_benchmark.java_vm_registry

    def with_host_vm(self, host_vm):
        return self.__class__(self.config_name(), self._options, host_vm)

    def run(self, cwd, args):
        args += self._options
        if hasattr(self.host_vm(), 'run_launcher'):
            return self.host_vm().run_launcher('node', args + self._options, cwd)
        else:
            out = mx.TeeOutputCapture(mx.OutputCapture())
            args = self.host_vm().post_process_command_line_args(args)
            mx.log("Running {} with args: {}".format(self.name(), args))
            code = mx_graal_nodejs.node(args, add_graal_vm_args=False, nonZeroIsFatal=False, out=out, err=out, cwd=cwd)
            out = out.underlying.data
            dims = self.host_vm().dimensions(cwd, args, code, out)
            return code, out, dims


def register_nodejs_vms():
    if mx.suite('nodejs-benchmarks', fatalIfMissing=False):
        import mx_nodejs_benchmarks
        _suite = mx.suite('graal-nodejs')
        mx_nodejs_benchmarks.add_vm(GraalNodeJsVm('default', []), _suite, 10)
