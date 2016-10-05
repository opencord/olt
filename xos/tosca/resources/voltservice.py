from services.volt.models import VOLTService
from service import XOSService

class XOSVOLTService(XOSService):
    provides = "tosca.nodes.VOLTService"
    xos_model = VOLTService
    copyin_props = ["view_url", "icon_url", "kind", "enabled", "published", "public_key", "private_key_fn", "versionNumber"]
