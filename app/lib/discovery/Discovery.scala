package lib.discovery

import com.typesafe.config.ConfigFactory
import org.apache.curator.CuratorConnectionLossException
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.x.discovery.{ServiceInstance, UriSpec, ServiceDiscoveryBuilder}
import org.apache.zookeeper.KeeperException.ConnectionLossException
import play.api.Logger

/**
 * The Zookeeper class takes care of communicating with Zookeeper for service discovery
 */

case class MagneticServiceInstance(name: String, host: String, port: Int, vrn: String)

class Discovery {

  val conf = ConfigFactory.load()

  val zkConnect = conf.getString("discovery.zookeeper.connect")
  val zkRoot = conf.getString("discovery.zookeeper.root")

  val retryPolicy = new ExponentialBackoffRetry(1000, 3)
  val zkClient = CuratorFrameworkFactory.newClient(zkConnect,retryPolicy)

  try {

    zkClient.start()

  } catch {

    case cle : CuratorConnectionLossException => Logger.error("Connection to Zookeeper lost")
    case e : Exception => Logger.error("Error connection to Zookeeper")

  } finally {


  }

  val serviceDiscovery =  ServiceDiscoveryBuilder
    .builder(null)
    .client(zkClient)
    .basePath(zkRoot)
    .build()

  try {
    serviceDiscovery.start()

  } catch {

    case cl : ConnectionLossException => Logger.error("Error connecting to Zookeeper for service")

  }


  def registerService(srv: MagneticServiceInstance) = {

    val builder = ServiceInstance.builder()
    val instance : ServiceInstance[Nothing] = builder
      .address(srv.host)
      .port(srv.port)
      .name(srv.name)
      .id(srv.vrn)
      .build()

    try {

      serviceDiscovery.registerService(instance)

    } catch {

     case cl : ConnectionLossException => Logger.error("Connection lost to Zookeeper")
    }


  }
}
