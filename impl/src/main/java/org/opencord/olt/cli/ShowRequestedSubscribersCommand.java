package org.opencord.olt.cli;


import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.net.ConnectPoint;
import org.opencord.olt.impl.OltFlowServiceInterface;

import java.util.Map;

@Service
@Command(scope = "onos", name = "volt-requested-subscribers",
        description = "Shows subscribers programmed by the operator. " +
                "Their data-plane status depends on the ONU status.")
public class ShowRequestedSubscribersCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "deviceId", description = "Access device ID",
            required = false, multiValued = false)
    @Completion(DeviceIdCompleter.class)
    private String strDeviceId = null;

    @Argument(index = 1, name = "port", description = "Subscriber port number",
            required = false, multiValued = false)
    @Completion(OltUniPortCompleter.class)
    private String strPort = null;

    @Override
    protected void doExecute() {
        OltFlowServiceInterface service = AbstractShellCommand.get(OltFlowServiceInterface.class);
        Map<ConnectPoint, Boolean> info = service.getRequestedSusbcribers();
        info.forEach(this::display);
    }

    private void display(ConnectPoint cp, Boolean status) {
        print("location=%s provisioned=%s", cp, status);
    }
}