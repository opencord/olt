
# Copyright 2017-present Open Networking Foundation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


from services.volt.models import AccessDevice, VOLTDevice
from xosresource import XOSResource

class XOSAccessDevice(XOSResource):
    provides = "tosca.nodes.AccessDevice"
    xos_model = AccessDevice
    copyin_props = ["uplink", "vlan"]
    name_field = None

    def get_xos_args(self, throw_exception=True):
        args = super(XOSAccessDevice, self).get_xos_args()

        volt_device_name = self.get_requirement("tosca.relationships.MemberOfDevice", throw_exception=throw_exception)
        if volt_device_name:
            args["volt_device"] = self.get_xos_object(VOLTDevice, throw_exception=throw_exception, name=volt_device_name)

        return args

    # AccessDevice has no name field, so we rely on matching the keys. We assume
    # the for a given VOLTDevice, there is only one AccessDevice per (uplink, vlan)
    # pair.

    def get_existing_objs(self):
        args = self.get_xos_args(throw_exception=False)
        volt_device = args.get("volt_device", None)
        uplink = args.get("uplink", None)
        vlan = args.get("vlan", None)
        if (volt_device is not None) and (uplink is not None) and (vlan is not None):
            existing_obj = self.get_xos_object(AccessDevice, volt_device=volt_device, uplink=uplink, vlan=vlan, throw_exception=False)
            if existing_obj:
                return [ existing_obj ]
        return []

