from django.db import models
from django.db.models import *
from core.models import Service, XOSBase, Slice, Instance, Tenant, TenantWithContainer, Node, Image, User, Flavor, TenantRoot, NetworkParameter, NetworkParameterType, Port, AddressPool, User
from core.models.xosbase import StrippedCharField
import os
from django.db import models, transaction
from django.forms.models import model_to_dict
from django.db.models import Q
from operator import itemgetter, attrgetter, methodcaller
from core.models import Tag
from core.models.service import LeastLoadedNodeScheduler
from services.vrouter.models import VRouterService, VRouterTenant
from services.rcord.models import CordSubscriberRoot
import traceback
from xos.exceptions import *
from xos.config import Config

class ConfigurationError(Exception):
    pass

VOLT_KIND = "vOLT"

CORD_USE_VTN = getattr(Config(), "networking_use_vtn", False)
