def __init__(self, *args, **kwargs):
    volt_services = VOLTService.objects.all()
    if volt_services:
        self._meta.get_field("owner").default = volt_services[0].id
    super(VOLTTenant, self).__init__(*args, **kwargs)
    self.cached_vcpe = None

@property
def vcpe(self):
    # TODO: hardcoded service dependency
    from services.vsg.models import VSGTenant

    vsg = None
    for link in self.subscribed_links:
        # cast from base class to derived class
        vsgs = VSGTenant.objects.filter(serviceinstance_ptr=link.provider_service_instance)
        if vsgs:
            vsg = vsgs[0]

    if not vsg:
        return None

    # always return the same object when possible
    if (self.cached_vcpe) and (self.cached_vcpe.id == vsg.id):
        return self.cached_vcpe

    vsg.caller = self.creator
    self.cached_vcpe = vsg
    return vsg

@vcpe.setter
def vcpe(self, value):
    raise XOSConfigurationError("vOLT.vCPE cannot be set this way -- create a new vCPE object and set its subscriber_tenant instead")

@property
def subscriber(self):
    for link in self.provided_links:
        # cast from base class to derived class
        roots = CordSubscriberRoot.objects.filter(serviceinstance_ptr=link.subscriber_service_instance)
        if roots:
            return roots[0]
    return None

def save(self, *args, **kwargs):
    if not self.creator:
        if not getattr(self, "caller", None):
            # caller must be set when creating a vCPE since it creates a slice
            raise XOSProgrammingError("VOLTTenant's self.caller was not set")
        self.creator = self.caller
        if not self.creator:
            raise XOSProgrammingError("VOLTTenant's self.creator was not set")

    super(VOLTTenant, self).save(*args, **kwargs)
