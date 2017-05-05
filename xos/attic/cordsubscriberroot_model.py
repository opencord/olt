# 'simple_attributes' will be expanded into properties and setters that
# store the attribute using self.set_attribute / self.get_attribute.

simple_attributes = ( ("devices", []), )

sync_attributes = ("firewall_enable",
                   "firewall_rules",
                   "url_filter_enable",
                   "url_filter_rules",
                   "cdn_enable",
                   "uplink_speed",
                   "downlink_speed",
                   "enable_uverse",
                   "status")

def __init__(self, *args, **kwargs):
    super(CordSubscriberRoot, self).__init__(*args, **kwargs)
    self.cached_volt = None

@property
def volt(self):
    volt = self.get_newest_subscribed_tenant(VOLTTenant)
    if not volt:
        return None

    # always return the same object when possible
    if (self.cached_volt) and (self.cached_volt.id == volt.id):
        return self.cached_volt

    #volt.caller = self.creator
    self.cached_volt = volt
    return volt

def find_device(self, mac):
    for device in self.devices:
        if device["mac"] == mac:
            return device
    return None

def update_device(self, mac, **kwargs):
    # kwargs may be "level" or "mac"
    #    Setting one of these to None will cause None to be stored in the db
    devices = self.devices
    for device in devices:
        if device["mac"] == mac:
            for arg in kwargs.keys():
                device[arg] = kwargs[arg]
            self.devices = devices
            return device
    raise ValueError("Device with mac %s not found" % mac)

def create_device(self, **kwargs):
    if "mac" not in kwargs:
        raise XOSMissingField("The mac field is required")

    if self.find_device(kwargs['mac']):
            raise XOSDuplicateKey("Device with mac %s already exists" % kwargs["mac"])

    device = kwargs.copy()

    devices = self.devices
    devices.append(device)
    self.devices = devices

    return device

def delete_device(self, mac):
    devices = self.devices
    for device in devices:
        if device["mac"]==mac:
            devices.remove(device)
            self.devices = devices
            return

    raise ValueError("Device with mac %s not found" % mac)

#--------------------------------------------------------------------------
# Deprecated -- devices used to be called users

def find_user(self, uid):
    return self.find_device(uid)

def update_user(self, uid, **kwargs):
    return self.update_device(uid, **kwargs)

def create_user(self, **kwargs):
    return self.create_device(**kwargs)

def delete_user(self, uid):
    return self.delete_user(uid)

# ------------------------------------------------------------------------

@property
def services(self):
    return {"cdn": self.cdn_enable,
            "url_filter": self.url_filter_enable,
            "firewall": self.firewall_enable}

@services.setter
def services(self, value):
    pass

def save(self, *args, **kwargs):
    self.validate_unique_service_specific_id(none_okay=True)
    if (not hasattr(self, 'caller') or not self.caller.is_admin):
        if (self.has_field_changed("service_specific_id")):
            raise XOSPermissionDenied("You do not have permission to change service_specific_id")
    super(CordSubscriberRoot, self).save(*args, **kwargs)

    # TODO - reimplement this as a watcher

    if (self.volt) and (self.volt.vcpe):
        # 1) trigger manage_bbs_account to run
        # 2) trigger vcpe observer to wake up
        self.volt.vcpe.save()
