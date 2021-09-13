package org.opencord.olt.cli;


import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.net.ConnectPoint;
import org.opencord.olt.impl.OltFlowServiceInterface;
import org.opencord.sadis.UniTagInformation;

import java.util.Map;
import java.util.Set;

@Service
@Command(scope = "onos", name = "volt-programmed-subscribers",
        description = "Shows subscribers programmed in the dataplane")
public class ShowProgrammedSubscribersCommand extends AbstractShellCommand {

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
        Map<ConnectPoint, Set<UniTagInformation>> info = service.getProgrammedSusbcribers();
        info.forEach(this::display);
    }

    private void display(ConnectPoint cp, Set<UniTagInformation> uniTagInformation) {
        uniTagInformation.forEach(uniTag ->
                print("location=%s tagInformation=%s", cp, uniTag));
    }
}