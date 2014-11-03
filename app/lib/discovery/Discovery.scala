package lib.discovery


import java.net.InetSocketAddress

import com.typesafe.config.ConfigFactory
import org.apache.zookeeper.KeeperException
import play.api.Logger
import com.loopfor.zookeeper._
import scala.concurrent.duration._

/**
 * The Zookeeper class takes care of communicating with Zookeeper for service discovery
 */

case class MagneticServiceInstance(serviceType: String, host: String, port: Int, vrn: String)


class Discovery {

  val conf = ConfigFactory.load()

  val zkConnect = conf.getString("discovery.zookeeper.connect")
  val zkRoot = conf.getString("discovery.zookeeper.root")
  val config = Configuration {
    Seq[InetSocketAddress](("10.212.220.216", 2181),("10.189.115.2", 2181),("10.44.147.14",2181)) } withTimeout{ 60.seconds } withWatcher {(event, session) =>

    Logger.info("Recieved Zookeeper event: " + event.toString)

  }

  val client = SynchronousZookeeper(this.config)


  /**
   * Update a node in Zookeeper
   * @param payload a payload to set in the node
   * @param path the path to the node
   */
  def setNode(payload: String, path: String)  = {

    val bytes = payload.map(_.toByte).toArray
    val acl = new ACL(Id.Anyone, ACL.All)
    val totalPath = s"/$zkRoot/$path"

    val exists = this.client.exists(totalPath)

    // check if the node exists. If not create it, otherwise update it
    exists match {

      case None =>

        Logger.info(s"Creating zkNode: $totalPath")
        try {
          val ZkResponse = this.client.create(totalPath, bytes, List(acl), Persistent)
          Logger.info("Zookeeper node created: " + ZkResponse)
        } catch {
          case e: Exception => Logger.error("Zookeeper error: " + e.toString)
        }

      case Some(status: Status) =>

        Logger.info(s"Updating zkNode: $totalPath")
        try {
          val ZkStatus: Status = client.set(totalPath, bytes, None)
          Logger.info("Zookeeper node updated: " + ZkStatus.path)
        } catch {
          case cle: ConnectionLossException => Logger.error("Lost connection to Zookeeper")
        }


    }
  }

  /**
   * delete a service from the registry
   * @param vrn the vrn of the service instance
   */
  def unRegisterService(vrn: String) = {

    val path = s"/$zkRoot/$vrn"

    // if the node, for some reason, is not there,there is no reason to delete it
    val directoryStatus : Option[Status] = client.exists(path)

    directoryStatus.map {

      case stat: Status =>

        Logger.info(s"Deleting zkNode: $path")

        try {
          this.client.delete(path,None)


        } catch {
          case cle: KeeperException.ConnectionLossException => Logger.error("Lost connection to Zookeeper")

        }

      case _ =>
        Logger.info(s"No zkNode $path found for deletion")

    }
  }
}
