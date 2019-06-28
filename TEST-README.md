How to test technology profile
------------------------------

- Export ONOS directory:

```
export ONOS_ROOT=~/voltha-projects/onos
source $ONOS_ROOT/tools/dev/bash_profile
```

- Build and run ONOS:

```
mvn clean install -DskipTests -Dcheckstyle.skip=true OR onos-buck build onos
ok clean
```

- Build SADIS and OLT apps:

```
mvn clean install
```

- Install and Activate SADIS and OLT apps:

```
onos-app localhost install ~/voltha-projects/sadis/app/target/sadis-app-3.0.0.oar
onos-app localhost activate org.opencord.sadis

onos-app localhost install ~/voltha-projects/olt/app/target/olt-app-3.0.1.oar
onos-app localhost activate org.opencord.olt
```

- To test with the oldest configuration (as oldest SADIS) - it includes only S and C tags:

```
onos-netcfg localhost  ~/voltha-projects/olt/app/src/main/resources/cfg.json
```

- To test AT&T use case:
    - Note:the tech profile table id is 10 to work with Mininet - if Voltha is used, then it will be 64 or greater value

```
onos-netcfg localhost  ~/voltha-projects/olt/app/src/main/resources/vlan_cfg.json
```

- To test DT use case (any vlan):
    - Note:the tech profile table id is 10 to work with Mininet - if Voltha is used, then it will be 64 or greater value

```
onos-netcfg localhost  ~/voltha-projects/olt/app/src/main/resources/any_vlan_cfg.json
```

- To test without VOLTHA, you can use Mininet with UserSwitch and emulate the topology:

```
sudo mn --custom ~/voltha-projects/olt/app/src/main/resources/custom-topo.py --switch user --controller=remote,ip=127.0.0.1,port=6633
```

- Run ONOS client:

```
 cd /tmp/onos-1.13.6/apache-karaf-3.0.8/bin
 ./client
```

- Check devices, flows and ports:

```
devices

the output (note that: the driver is pmc-olt):

id=of:0000000000000001, available=true, local-status=connected 2s ago, role=MASTER, type=SWITCH, mfr=Stanford University, Ericsson Research and CPqD Research, hw=OpenFlow 1.3 Reference Userspace Switch, sw=Sep 10 2018 16:56:31, serial=1, chassis=1, driver=pmc-olt, channelId=127.0.0.1:36022, locType=none, managementAddress=127.0.0.1, name=of:0000000000000001, protocol=OF_13

flows

the output:

deviceId=of:0000000000000001, flowRuleCount=4
    id=ac00002f18dba3, state=ADDED, bytes=0, packets=0, duration=0, liveType=UNKNOWN, priority=10000, tableId=0, appId=org.opencord.olt, payLoad=null, selector=[IN_PORT:1, ETH_TYPE:eapol], treatment=DefaultTrafficTreatment{immediate=[OUTPUT:CONTROLLER], deferred=[], transition=None, meter=[], cleared=false, StatTrigger=null, metadata=null}
    id=ac00005512664b, state=ADDED, bytes=0, packets=0, duration=0, liveType=UNKNOWN, priority=10000, tableId=0, appId=org.opencord.olt, payLoad=null, selector=[IN_PORT:LOCAL, ETH_TYPE:eapol], treatment=DefaultTrafficTreatment{immediate=[OUTPUT:CONTROLLER], deferred=[], transition=None, meter=[], cleared=false, StatTrigger=null, metadata=null}
    id=ac00005d0fea43, state=ADDED, bytes=0, packets=0, duration=0, liveType=UNKNOWN, priority=10000, tableId=0, appId=org.opencord.olt, payLoad=null, selector=[IN_PORT:2, ETH_TYPE:lldp], treatment=DefaultTrafficTreatment{immediate=[OUTPUT:CONTROLLER], deferred=[], transition=None, meter=[], cleared=false, StatTrigger=null, metadata=null}
    id=ac0000617983d6, state=ADDED, bytes=0, packets=0, duration=0, liveType=UNKNOWN, priority=10000, tableId=0, appId=org.opencord.olt, payLoad=null, selector=[IN_PORT:2, ETH_TYPE:ipv4, IP_PROTO:17, UDP_SRC:68, UDP_DST:67], treatment=DefaultTrafficTreatment{immediate=[OUTPUT:CONTROLLER], deferred=[], transition=None, meter=[], cleared=false, StatTrigger=null, metadata=null}

ports

the output (assume that port 1 is UNI and 2 is NNI):

id=of:0000000000000001, available=true, local-status=connected 7s ago, role=MASTER, type=SWITCH, mfr=Stanford University, Ericsson Research and CPqD Research, hw=OpenFlow 1.3 Reference Userspace Switch, sw=Sep 10 2018 16:56:31, serial=1, chassis=1, driver=pmc-olt, channelId=127.0.0.1:36022, locType=none, managementAddress=127.0.0.1, name=of:0000000000000001, protocol=OF_13
  port=LOCAL, state=enabled, type=copper, speed=10 , adminState=enabled, portMac=00:00:00:00:00:01, portName=tap:
  port=1, state=enabled, type=copper, speed=10485 , adminState=enabled, portMac=ba:5d:49:53:9f:d2, portName=s1-eth1
  port=2, state=enabled, type=copper, speed=10485 , adminState=enabled, portMac=9e:11:ee:ba:4f:f4, portName=s1-eth

```

- Add subscriber to UNI port:

```
volt-add-subscriber-access of:0000000000000001 1
```

- The flows when you use cfg.json:

```
Upstream Flows:

    id=ac000000f780f6, state=ADDED, bytes=0, packets=0, duration=5, liveType=UNKNOWN, priority=1000, tableId=0, appId=org.opencord.olt, payLoad=null, selector=[IN_PORT:1, VLAN_VID:0], treatment=DefaultTrafficTreatment{immediate=[VLAN_ID:2], deferred=[], transition=TABLE:1, meter=[], cleared=false, StatTrigger=null, metadata=null}
    id=ac0000c9fd17bf, state=ADDED, bytes=0, packets=0, duration=5, liveType=UNKNOWN, priority=1000, tableId=1, appId=org.opencord.olt, payLoad=null, selector=[IN_PORT:1, VLAN_VID:2], treatment=DefaultTrafficTreatment{immediate=[VLAN_PUSH:vlan, VLAN_ID:4], deferred=[OUTPUT:2], transition=None, meter=[], cleared=false, StatTrigger=null, metadata=null}

Downstream Flows:

    id=ac000022a77664, state=ADDED, bytes=0, packets=0, duration=5, liveType=UNKNOWN, priority=1000, tableId=0, appId=org.opencord.olt, payLoad=null, selector=[IN_PORT:2, METADATA:200000001, VLAN_VID:4], treatment=DefaultTrafficTreatment{immediate=[VLAN_POP], deferred=[], transition=TABLE:1, meter=[], cleared=false, StatTrigger=null, metadata=null}
    id=ac00001801d6d1, state=ADDED, bytes=0, packets=0, duration=5, liveType=UNKNOWN, priority=1000, tableId=1, appId=org.opencord.olt, payLoad=null, selector=[IN_PORT:2, VLAN_VID:2], treatment=DefaultTrafficTreatment{immediate=[VLAN_POP, VLAN_ID:0], deferred=[OUTPUT:1], transition=None, meter=[], cleared=false, StatTrigger=null, metadata=null}
```

- The flows when you use vlan_cfg.json:

```
Upstream Flows:

    id=ac000000f780f6, state=ADDED, bytes=0, packets=0, duration=6, liveType=UNKNOWN, priority=1000, tableId=0, appId=org.opencord.olt, payLoad=null, selector=[IN_PORT:1, VLAN_VID:0], treatment=DefaultTrafficTreatment{immediate=[VLAN_ID:2], deferred=[], transition=TABLE:1, meter=[], cleared=false, StatTrigger=null, metadata=null}
    id=ac0000c9fd17bf, state=ADDED, bytes=0, packets=0, duration=6, liveType=UNKNOWN, priority=1000, tableId=1, appId=org.opencord.olt, payLoad=null, selector=[IN_PORT:1, VLAN_VID:2], treatment=DefaultTrafficTreatment{immediate=[VLAN_PUSH:vlan, VLAN_ID:4], deferred=[OUTPUT:2], transition=TABLE:10, meter=[METER:1], cleared=false, StatTrigger=null, metadata=null}

Downstream Flows:

    id=ac000022a77664, state=ADDED, bytes=0, packets=0, duration=6, liveType=UNKNOWN, priority=1000, tableId=0, appId=org.opencord.olt, payLoad=null, selector=[IN_PORT:2, METADATA:200000001, VLAN_VID:4], treatment=DefaultTrafficTreatment{immediate=[VLAN_POP], deferred=[], transition=TABLE:1, meter=[], cleared=false, StatTrigger=null, metadata=null}
    id=ac00001801d6d1, state=ADDED, bytes=0, packets=0, duration=6, liveType=UNKNOWN, priority=1000, tableId=1, appId=org.opencord.olt, payLoad=null, selector=[IN_PORT:2, VLAN_VID:2], treatment=DefaultTrafficTreatment{immediate=[VLAN_POP, VLAN_ID:0], deferred=[OUTPUT:1], transition=TABLE:10, meter=[METER:2], cleared=false, StatTrigger=null, metadata=null}

Upstream Meter:

 DefaultMeter{device=of:0000000000000001, cellId=1, appId=org.opencord.olt, unit=KB_PER_SEC, isBurst=true, state=ADDED, bands=[DefaultBand{rate=200000000, burst-size=348000, type=DROP, drop-precedence=null}, DefaultBand{rate=10000000, burst-size=348000, type=DROP, drop-precedence=null}, DefaultBand{rate=10000000, burst-size=0, type=DROP, drop-precedence=null}]}

Downstream Meter:

 DefaultMeter{device=of:0000000000000001, cellId=2, appId=org.opencord.olt, unit=KB_PER_SEC, isBurst=true, state=ADDED, bands=[DefaultBand{rate=300000000, burst-size=348000, type=DROP, drop-precedence=null}, DefaultBand{rate=20000000, burst-size=348000, type=DROP, drop-precedence=null}, DefaultBand{rate=30000000, burst-size=0, type=DROP, drop-precedence=null}]}
```

- The flows when you use any_vlan_cfg.json:

```
Upstream Flows:

    id=ac000009d37362, state=ADDED, bytes=0, packets=0, duration=3, liveType=UNKNOWN, priority=500, tableId=0, appId=org.opencord.olt, payLoad=null, selector=[IN_PORT:1], treatment=DefaultTrafficTreatment{immediate=[NOACTION], deferred=[], transition=None, meter=[], cleared=false, StatTrigger=null, metadata=null}
    id=ac0000fb6f690a, state=ADDED, bytes=0, packets=0, duration=3, liveType=UNKNOWN, priority=1000, tableId=0, appId=org.opencord.olt, payLoad=null, selector=[IN_PORT:1, VLAN_VID:Any], treatment=DefaultTrafficTreatment{immediate=[], deferred=[], transition=TABLE:1, meter=[], cleared=false, StatTrigger=null, metadata=null}
    id=ac0000076e2984, state=ADDED, bytes=0, packets=0, duration=3, liveType=UNKNOWN, priority=1000, tableId=1, appId=org.opencord.olt, payLoad=null, selector=[IN_PORT:1, VLAN_VID:Any], treatment=DefaultTrafficTreatment{immediate=[VLAN_PUSH:qinq, VLAN_ID:4], deferred=[OUTPUT:2], transition=TABLE:10, meter=[METER:1], cleared=false, StatTrigger=null, metadata=null}

Downstream Flows:

    id=ac000022a77664, state=ADDED, bytes=0, packets=0, duration=3, liveType=UNKNOWN, priority=1000, tableId=0, appId=org.opencord.olt, payLoad=null, selector=[IN_PORT:2, METADATA:200000001, VLAN_VID:4], treatment=DefaultTrafficTreatment{immediate=[], deferred=[VLAN_POP], transition=TABLE:1, meter=[], cleared=false, StatTrigger=null, metadata=null}
    id=ac0000911ae9cb, state=ADDED, bytes=0, packets=0, duration=3, liveType=UNKNOWN, priority=1000, tableId=1, appId=org.opencord.olt, payLoad=null, selector=[IN_PORT:2, VLAN_VID:Any], treatment=DefaultTrafficTreatment{immediate=[], deferred=[OUTPUT:1], transition=TABLE:10, meter=[METER:2], cleared=false, StatTrigger=null, metadata=null}

Upstream Meter:

 DefaultMeter{device=of:0000000000000001, cellId=1, appId=org.opencord.olt, unit=KB_PER_SEC, isBurst=true, state=ADDED, bands=[DefaultBand{rate=200000000, burst-size=348000, type=DROP, drop-precedence=null}, DefaultBand{rate=10000000, burst-size=348000, type=DROP, drop-precedence=null}, DefaultBand{rate=10000000, burst-size=0, type=DROP, drop-precedence=null}]}

Downstream Meter:

 DefaultMeter{device=of:0000000000000001, cellId=2, appId=org.opencord.olt, unit=KB_PER_SEC, isBurst=true, state=ADDED, bands=[DefaultBand{rate=300000000, burst-size=348000, type=DROP, drop-precedence=null}, DefaultBand{rate=20000000, burst-size=348000, type=DROP, drop-precedence=null}, DefaultBand{rate=30000000, burst-size=0, type=DROP, drop-precedence=null}]}
```
