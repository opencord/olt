
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


from core.models import User, ServiceInstanceLink
from services.volt.models import VOLTTenant, VOLTService, VOLT_KIND
from services.rcord.models import CordSubscriberRoot

from xosresource import XOSResource

class XOSVOLTTenant(XOSResource):
    provides = "tosca.nodes.VOLTTenant"
    xos_model = VOLTTenant
    copyin_props = ["service_specific_id", "s_tag", "c_tag"]
    name_field = None

    def get_xos_args(self, throw_exception=True):
        args = super(XOSVOLTTenant, self).get_xos_args()

        provider_name = self.get_requirement("tosca.relationships.MemberOfService", throw_exception=throw_exception)
        if provider_name:
            args["owner"] = self.get_xos_object(VOLTService, throw_exception=throw_exception, name=provider_name)

        return args

    def get_existing_objs(self):
        args = self.get_xos_args(throw_exception=False)
        provider_service = args.get("owner", None)
        service_specific_id = args.get("service_specific_id", None)
        if (provider_service) and (service_specific_id):
            existing_obj = self.get_xos_object(VOLTTenant, owner=provider_service, service_specific_id=service_specific_id, throw_exception=False)
            if existing_obj:
                return [ existing_obj ]
        return []

    def postprocess(self, obj):
        subscriber_name = self.get_requirement("tosca.relationships.BelongsToSubscriber")
        if subscriber_name:
            subscriber = self.get_xos_object(CordSubscriberRoot, throw_exception=True, name=subscriber_name)

            links = ServiceInstanceLink.objects.filter(provider_service_instance = obj,
                                                       subscriber_service_instance = subscriber)
            if not links:
                link = ServiceInstanceLink(provider_service_instance = obj, subscriber_service_instance = subscriber)
                link.save()

    def can_delete(self, obj):
        return super(XOSVOLTTenant, self).can_delete(obj)

