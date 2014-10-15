package lib.discovery


import java.net.InetSocketAddress

import com.typesafe.config.ConfigFactory
import org.apache.zookeeper.KeeperException
import play.api.Logger
import com.loopfor.zookeeper._
import scala.concurrent.Future
import scala.concurrent.duration._
import play.api.libs.json.{JsValue, Json}
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * The Zookeeper class takes care of communicating with Zookeeper for service discovery
 */

case class MagneticServiceInstance(serviceType: String, host: String, port: Int, vrn: String)


class Discovery {

  val conf = ConfigFactory.load()

  val zkConnect = conf.getString("discovery.zookeeper.connect")
  val zkRoot = conf.getString("discovery.zookeeper.root")
  val serviceEndPoint = conf.getString("loadbalancer.host")

  val config = Configuration {

    Seq[InetSocketAddress](("10.151.59.229", 2181),("10.101.29.217", 2181),("10.195.59.140",2181)) } withTimeout{ 60.seconds } withWatcher {(event, session) =>

    Logger.info("Zookeeper event: " + event.toString)

  }

  val client = AsynchronousZookeeper(config)

  /**
   * Registers a service in Zookeeper
   * @param srv an instance of [[MagneticServiceInstance]]
   */
  def registerService(srv: MagneticServiceInstance) = {

    implicit val instanceWrites = Json.writes[MagneticServiceInstance]

    val zkMessage : JsValue = Json.obj(
      "name" -> srv.vrn,
      "bindPort" -> srv.port,
      "endPoint" -> this.serviceEndPoint,
      "mode" -> "tcp"
  )

    val data = Json.stringify(Json.toJson(zkMessage))
    val bytes = data.map(_.toByte).toArray

    val acl = new ACL(Id.Anyone, ACL.All)
    val path = s"/$zkRoot/${srv.vrn}"


    Logger.info(s"Creating zkNode: $path")

    var futureZkResponse : Future[String] = null

    try {
      futureZkResponse = client.create(path,bytes,List(acl),Persistent)

    } catch {

      case cle: ConnectionLossException => Logger.error("Lost connection to Zookeeper")
    }

    futureZkResponse.map {

      case s: String => Logger.info(s"Zookeeper response:" + s)

     }

  }

  /**
   * delete a service from the registry
   * @param vrn the vrn of the service instance
   */
  def unRegisterService(vrn: String) = {

    val path = s"/$zkRoot/$vrn"

    // if the node, for some reason, is not there,there is no reason to delete it
    val directoryStatus : Future[Option[Status]] = client.exists(path)

    directoryStatus.map {

      case Some(stat: Status) =>

        Logger.info(s"Deleting zkNode: $path")

        try {
          client.delete(path,None)


        } catch {
          case cle: KeeperException.ConnectionLossException => Logger.error("Lost connection to Zookeeper")

        }

      case None =>
        Logger.info(s"No zkNode $path found for deletion")

    }
  }
}
