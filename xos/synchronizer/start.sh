export XOS_DIR=/opt/xos
nohup python olt-synchronizer.py  -C $XOS_DIR/synchronizers/olt/olt_synchronizer_config > /dev/null 2>&1 &
