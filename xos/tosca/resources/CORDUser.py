
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


from core.models import User
from services.rcord.models import CordSubscriberRoot

from xosresource import XOSResource

class XOSCORDUser(XOSResource):
    provides = "tosca.nodes.CORDUser"

    def get_model_class_name(self):
        return "CORDUser"

    def get_subscriber_root(self, throw_exception=True):
        sub_name = self.get_requirement("tosca.relationships.SubscriberDevice", throw_exception=throw_exception)
        sub = self.get_xos_object(CordSubscriberRoot, name=sub_name, throw_exception=throw_exception)
        return sub

    def get_existing_objs(self):
        result = []
        sub = self.get_subscriber_root(throw_exception=False)
        if not sub:
           return []
        for user in sub.devices:
            if user["name"] == self.obj_name:
                result.append(user)
        return result

    def get_xos_args(self):
        args = {"name": self.obj_name,
                "level": self.get_property("level"),
                "mac": self.get_property("mac")}
        return args


    def create(self):
        xos_args = self.get_xos_args()
        sub = self.get_subscriber_root()

        sub.create_device(**xos_args)
        sub.save()

        self.info("Created CORDUser %s for Subscriber %s" % (self.obj_name, sub.name))

    def update(self, obj):
        pass

    def delete(self, obj):
        if (self.can_delete(obj)):
            self.info("destroying CORDUser %s" % obj["name"])
            sub = self.get_subscriber_root()
            sub.delete_user(obj["id"])
            sub.save()

    def can_delete(self, obj):
        return True

