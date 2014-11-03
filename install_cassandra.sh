#!/bin/bash

set -x

# Installs Cassandra-mesos on a set of hosts using simple SSH

export ZOOKEEPER='10.212.220.216:2181'
export NODES=3

sudo su - << SCRIPT

echo "=> Preparing Cassandra-mesos install..."

cd /root

echo "=> Downloading Cassandra-mesos..."

curl -O http://downloads.mesosphere.io/cassandra/cassandra-mesos-2.0.5-1.tgz

tar -xzvf cassandra-mesos-2.0.5-1.tgz

cd cassandra-mesos-2.0.5-1


echo "=> Setting Zookeepers for coordination..."

sed -e "s#mesos.master.url: 'zk://localhost:2181/mesos'#mesos.master.url: 'zk://${ZOOKEEPER}/mesos'#g" -i conf/mesos.yaml

echo "=> Setting Zookeepers for state..."
echo "state.zk: '${ZOOKEEPER}/state'" >> conf/mesos.yaml

echo "=> Setting Number of nodes to ${NODES}..."
sed -e s/'cassandra.noOfHwNodes: 1'/'cassandra.noOfHwNodes: ${NODES}'/g -i conf/mesos.yaml

echo "=> Starting Cassandra..."

bin/cassandra-mesos > /dev/null 2>&1 &

SCRIPT


