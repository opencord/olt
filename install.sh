sshpass -p karaf ssh -p 30115 -o userknownhostsfile=/dev/null -o stricthostkeychecking=no karaf@localhost app uninstall org.opencord.olt

sleep 5

onos-app -P 30120 -u karaf -p karaf 127.0.0.1 reinstall! app/target/olt-app-5.0.0-SNAPSHOT.oar

sshpass -p karaf ssh -p 30115 -o userknownhostsfile=/dev/null -o stricthostkeychecking=no karaf@localhost log:set TRACE org.opencord.olt
sshpass -p karaf ssh -p 30115 -o userknownhostsfile=/dev/null -o stricthostkeychecking=no karaf@localhost cfg set org.opencord.olt.impl.OltFlowService waitForRemoval true
