#!/bin/bash



# set -x

# installs Kafka on a set of hosts using simple SSH

echo "=> Preparing Kafka install..."

export ZOOKEEPERS='10.212.220.216:2181,10.189.115.2:2181,10.44.147.14:2181'


sudo su - << SCRIPT

echo "=> Downloading Kafka..."

curl -O http://apache.mirror1.spango.com/kafka/0.8.1.1/kafka_2.8.0-0.8.1.1.tgz

tar -xzvf kafka_2.8.0-0.8.1.1.tgz

cd kafka_2.8.0-0.8.1.1

echo "=> Setting Zookeepers for coordination..."

sed -e s/zookeeper.connect=localhost:2181/zookeeper.connect=${ZOOKEEPERS}/g -i config/server.properties

echo "=> Setting Log retention to 24 hrs..."

sed -e s/log.retention.hours=168/log.retention.hours=24/g -i config/server.properties

echo "=> Starting Kafka..."

bin/kafka-server-start.sh config/server.properties > /dev/null 2>&1 &


SCRIPT


