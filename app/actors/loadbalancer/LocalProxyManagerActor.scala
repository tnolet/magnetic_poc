package actors.loadbalancer

import akka.actor.{ActorRef, ActorLogging, Actor}
import com.typesafe.config.ConfigFactory
import models.loadbalancer.Configuration
import models.service.Services
import play.api.db.slick._
import play.api.libs.json.Json
import play.api.Play.current

/**
 * Manages the local proxy instances using Zookeeper as an intermediate. It creates, updates and deletes services in
 * in a JSON structure and pushes this to a known zookeeper node. This node is watched by all local proxies for
 * updates.
 */

trait LpMessage
case object PushServicesToLocalProxies extends LpMessage
case class RemoveServiceFromLocalProxies(vrn: String) extends  LpMessage

class LocalProxyManagerActor extends Actor with ActorLogging  {

  val conf = ConfigFactory.load()
  val serviceEndPoint = conf.getString("loadbalancer.host")
  var originalSender : ActorRef = _

  val zkClient = new lib.discovery.Discovery


  def receive = {

    case PushServicesToLocalProxies =>

      originalSender = sender()
      log.info("Adding service to local proxy configuration")

      var config : Configuration = Configuration(Frontends = List(), backends = List(), services = None)

      DB.withTransaction { implicit session =>

        Services.all.map { srv =>

            val newService = models.loadbalancer.Service(name = srv.vrn,
              bindPort = srv.port,
              endPoint = this.serviceEndPoint,
              mode = "tcp")

              config = Configuration.addService(config, newService)

            }

         this.zkClient.setNode(Json.stringify(Json.toJson(config)), "localproxy")



      }

      //this is really ugly...
    case srv : RemoveServiceFromLocalProxies =>

      originalSender = sender()
      log.info(s"Removing service ${srv.vrn} from local proxy configuration")


      // get all the services

      var config : Configuration = Configuration(Frontends = List(), backends = List(), services = None)

      DB.withTransaction { implicit session =>

        Services.all.map { srv =>

          val newService = models.loadbalancer.Service(name = srv.vrn,
            bindPort = srv.port,
            endPoint = this.serviceEndPoint,
            mode = "tcp")

          config = Configuration.addService(config, newService)

        }

        // remove the specific service from the whole config
        config = Configuration.removeService(config, srv.vrn)

        this.zkClient.setNode(Json.stringify(Json.toJson(config)), "localproxy")

      }
  }
}
