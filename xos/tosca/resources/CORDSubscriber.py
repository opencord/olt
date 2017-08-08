
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


#from core.models import User, TenantRootPrivilege, TenantRootRole
from services.rcord.models import CordSubscriberRoot
from xosresource import XOSResource

class XOSCORDSubscriber(XOSResource):
    provides = "tosca.nodes.CORDSubscriber"
    xos_model = CordSubscriberRoot
    copyin_props = ["service_specific_id", "firewall_enable", "url_filter_enable", "cdn_enable", "url_filter_level"]

#    def postprocess(self, obj):
#        rolemap = ( ("tosca.relationships.AdminPrivilege", "admin"), ("tosca.relationships.AccessPrivilege", "access"), )
#        self.postprocess_privileges(TenantRootRole, TenantRootPrivilege, rolemap, obj, "tenant_root")

    def can_delete(self, obj):
        return super(XOSCORDSubscriber, self).can_delete(obj)

