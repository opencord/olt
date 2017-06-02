def __init__(self, *args, **kwargs):
    volt_services = VOLTService.objects.all()
    if volt_services:
        self._meta.get_field("provider_service").default = volt_services[0].id
    super(VOLTTenant, self).__init__(*args, **kwargs)
    self.cached_vcpe = None

@property
def vcpe(self):
    from services.vsg.models import VSGTenant
    vcpe = self.get_newest_subscribed_tenant(VSGTenant)
    if not vcpe:
        return None

    # always return the same object when possible
    if (self.cached_vcpe) and (self.cached_vcpe.id == vcpe.id):
        return self.cached_vcpe

    vcpe.caller = self.creator
    self.cached_vcpe = vcpe
    return vcpe

@vcpe.setter
def vcpe(self, value):
    raise XOSConfigurationError("vOLT.vCPE cannot be set this way -- create a new vCPE object and set its subscriber_tenant instead")

@property
def subscriber(self):
    if not self.subscriber_root:
        return None
    subs = CordSubscriberRoot.objects.filter(id=self.subscriber_root.id)
    if not subs:
        return None
    return subs[0]

def save(self, *args, **kwargs):
    # VOLTTenant probably doesn't need a SSID anymore; that will be handled
    # by CORDSubscriberRoot...
    # self.validate_unique_service_specific_id()

    if (self.subscriber_root is not None):
        subs = self.subscriber_root.get_subscribed_tenants(VOLTTenant)
        if (subs) and (self not in subs):
            raise XOSDuplicateKey("Subscriber should only be linked to one vOLT")

    if not self.creator:
        if not getattr(self, "caller", None):
            # caller must be set when creating a vCPE since it creates a slice
            raise XOSProgrammingError("VOLTTenant's self.caller was not set")
        self.creator = self.caller
        if not self.creator:
            raise XOSProgrammingError("VOLTTenant's self.creator was not set")

    super(VOLTTenant, self).save(*args, **kwargs)


