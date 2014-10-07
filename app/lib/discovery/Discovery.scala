package lib.discovery

import com.typesafe.config.ConfigFactory
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.x.discovery.{ServiceInstance, UriSpec, ServiceDiscoveryBuilder}

/**
 * The Zookeeper class takes care of communicating with Zookeeper for service discovery
 */

case class MagneticServiceInstance(name: String, host: String, port: Int, vrn: String)

class Discovery {

  val conf = ConfigFactory.load()

  val zkHost = conf.getString("discovery.zookeeper.host")
  val zkPort = conf.getInt("discovery.zookeeper.port")
  val zkRoot = conf.getString("discovery.zookeeper.root")
  val zkBaseUri = s"$zkHost:$zkPort"

  val retryPolicy = new ExponentialBackoffRetry(1000, 3)
  val zkClient = CuratorFrameworkFactory.newClient(zkBaseUri,retryPolicy)
  zkClient.start()

  val serviceDiscovery =  ServiceDiscoveryBuilder
    .builder(null)
    .client(zkClient)
    .basePath(zkRoot)
    .build()
  serviceDiscovery.start()


  def registerService(srv: MagneticServiceInstance) = {

    val builder = ServiceInstance.builder()
    val instance : ServiceInstance[Nothing] = builder
      .address(srv.host)
      .port(srv.port)
      .name(srv.name)
      .id(srv.vrn)
      .build()

    serviceDiscovery.registerService(instance)


  }
}
