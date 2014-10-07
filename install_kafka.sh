#!/bin/bash


sudo su -

curl -O http://apache.mirror1.spango.com/kafka/0.8.1.1/kafka_2.8.0-0.8.1.1.tgz

tar -xzvf kafka_2.8.0-0.8.1.1.tgz

cd kafka_2.8.0-0.8.1.1

sed -i '/zookeeper.connect=localhost:2181/c\zookeeper.connect=10.151.59.229:2181,10.101.29.217:2181,10.195.59.140:2181' config/server.properties

bin/kafka-server-start.sh config/server.properties > /dev/null 2>&1 &



