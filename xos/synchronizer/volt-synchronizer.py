#!/usr/bin/env python

# This imports and runs ../../xos-observer.py

import importlib
import os
import sys
sys.path.append('/opt/xos')

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "xos.settings")

import synchronizers.base.event_loop
if hasattr(synchronizers.base.event_loop, "set_driver"):
    # VTN synchronizer needs the OpenStack driver
    from openstack_xos.driver import OpenStackDriver
    synchronizers.base.event_loop.set_driver(OpenStackDriver())

mod = importlib.import_module("synchronizers.base.xos-synchronizer")
mod.main()

