package org.opencord.olt.rest;

import org.onlab.rest.AbstractWebApplication;

import java.util.Set;

/**
 * Created by onos on 16-10-24.
 */
public class OltWebApplication extends AbstractWebApplication {
    @Override
    public Set<Class<?>> getClasses() {
        return getClasses(OltWebResource.class);
    }
}