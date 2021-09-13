package org.opencord.olt.cli;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;

@Service
@Command(scope = "onos", name = "volt-failed-subscribers",
        description = "Shows subscribers that failed provisioning")
public class ShowFailedSubscribersCommand extends AbstractShellCommand {

    @Override
    protected void doExecute() {
        print("Subscribers are stubborn creatures, they'll keep trying until they succeed!");
    }

}