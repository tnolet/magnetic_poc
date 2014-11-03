#!/bin/bash

set -x

# Installs KairosDB on a hosts using simple SSH

export CASSANDRA_NODES='10.46.116.50:9160,10.33.26.106:9160,10.190.31.107:9160'

sudo su - << SCRIPT

echo "=> Preparing KairosDB install..."

cd /root

echo "=> Downloading KairosDB..."

curl -OL https://github.com/kairosdb/kairosdb/releases/download/v0.9.4/kairosdb-0.9.4-6.tar.gz

tar -xzvf kairosdb-0.9.4-6.tar.gz

cd kairosdb

echo "=> Setting Jetty to port 8081..."

sed -e s/kairosdb.jetty.port=8080/kairosdb.jetty.port=8081/g -i conf/kairosdb.properties

echo "=> Setting Cassandra as backend..."

sed -e s/kairosdb.service.datastore=org.kairosdb.datastore.h2.H2Module/#kairosdb.service.datastore=org.kairosdb.datastore.h2.H2Module/g -i conf/kairosdb.properties
sed -e s/#kairosdb.service.datastore=org.kairosdb.datastore.cassandra.CassandraModule/kairosdb.service.datastore=org.kairosdb.datastore.cassandra.CassandraModule/g -i conf/kairosdb.properties

echo "=> Setting Cassandra nodes ${CASSANDRA_NODES}..."

sed -e s/kairosdb.datastore.cassandra.host_list=localhost:9160/kairosdb.datastore.cassandra.host_list=${CASSANDRA_NODES}/g -i conf/kairosdb.properties

echo "=> Starting KairosDB..."

bin/kairosdb.sh start

SCRIPT


