package actors.loadbalancer

import akka.actor.{ActorRef, Actor, ActorLogging}
import lib.loadbalancer.LoadBalancer
import models.docker.{ContainerInstance}
import models.loadbalancer._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


sealed trait LbMessage

//Creation and Deletion messages
case class AddFrontend(vrn: String, port: Int) extends LbMessage
case class AddBackend(vrn: String) extends LbMessage


case class AddBackendServer(servers: List[BackendServerCreate]) extends LbMessage
case class RemoveBackendServer( vrns: List[String]) extends LbMessage

case class AddFrontendBackend(vrn: String, port: Int, mode: String) extends LbMessage
case class RemoveFrontendBackend( vrn: String) extends LbMessage



//Update messages

case class UpdateBackendServerWeight(weight: Int, servers: List[ContainerInstance], serviceVrn: String) extends LbMessage

case object LbSuccess extends LbMessage
case object LbFail extends LbMessage
case object LbNotFound extends LbMessage


class LoadBalancerManagerActor extends Actor with ActorLogging {

  private var config: Configuration = _
  private var originalSender: ActorRef = _

  def receive =  {

    case bes : AddBackendServer =>

      originalSender = sender()
      log.info("Getting LB Configuration")

      // Get a Future on an Option of Config
      LoadBalancer.getConfig.map {

        case Some(config: Configuration) => {

          log.debug("Current load balancer configuration is: " + config.toString)

          var newConf : Configuration = config

          bes.servers.foreach { srv =>

            val newBackendServer = BackendServer(srv.vrn, srv.host, srv.port, srv.weight.getOrElse(0), None, Some(false), None)
            newConf = Configuration.addServerToBackend(newConf, srv.backend, newBackendServer)

          }

          log.debug("New load balancer configuration is:" + newConf.toString)

          val result = LoadBalancer.setConfig(newConf)
          handleSetConfigResponse(result,originalSender)
        }
        case None => {
          log.info("Loadbalancer provided an invalid configuration")
          originalSender ! LbFail
        }
      }

    case RemoveBackendServer(vrns) =>

      originalSender = sender()

      LoadBalancer.getConfig.map {
        case Some(config: Configuration) => {

          log.debug("Current load balancer configuration is: " + config.toString)

          var newConf : Configuration = config

          vrns.foreach { vrn =>

            newConf = Configuration.removeServerFromBackend(newConf, vrn)

          }

          log.debug("New load balancer configuration is:" + newConf.toString)

          val result = LoadBalancer.setConfig(newConf)
          handleSetConfigResponse(result,originalSender)

        }
        case None =>
          log.info("Loadbalancer provided an invalid configuration")
          originalSender ! LbFail

      }

    case fb: AddFrontendBackend =>

      originalSender = sender()

      // Get a Future on an Option of Config. First
      LoadBalancer.getConfig.map {
        case Some(config: Configuration) => {

          log.debug("Current load balancer configuration is: " + config.toString)

          val dummyBackendServer = BackendServer("dummy-vrn", "127.0.0.1", 9999, 0, None, None, None)
          val newBackend = Backend(fb.vrn, fb.mode, List(dummyBackendServer), Map("transparent" -> false))
          val newFrontend = Frontend(fb.vrn, fb.mode, fb.port, "0.0.0.0", fb.vrn, Map("transparent" -> false))

          val _newConf = Configuration.addBackend(config, newBackend)
          val newConf = Configuration.addFrontend(_newConf, newFrontend)

          log.debug("New load balancer configuration is:" + newConf.toString)

          val result = LoadBalancer.setConfig(newConf)
          handleSetConfigResponse(result,originalSender)

        }

        case None =>
          log.info("Loadbalancer provided an invalid configuration")
          originalSender ! LbFail
      }

    case RemoveFrontendBackend(vrn) =>

      originalSender = sender()

      // Get a Future on an Option of Config. First
      LoadBalancer.getConfig.map {
        case Some(config: Configuration) =>

          log.debug("Current load balancer configuration is: " + config.toString)

          val _newConf = Configuration.removeBackend(config,vrn)
          val newConf = Configuration.removeFrontend(_newConf,vrn)

          log.debug("New load balancer configuration is:" + newConf.toString)

          val result = LoadBalancer.setConfig(newConf)
          handleSetConfigResponse(result,originalSender)

        case None =>
          log.info("Loadbalancer provided an invalid configuration")
          originalSender ! LbFail

      }


    /**
     * Updating the backend server weight is done based on the containers VRN. The backend servers are ID's bij
     * the VRN's of the instances belonging to the container. To succeed, we get all the backend servers and update
     * all of their weights individually
     */
    case bes : UpdateBackendServerWeight =>

      val originalSender = sender()

      val length = bes.servers.length
      var success : Int = 0
      bes.servers.foreach { case server => {

        log.debug(s"Setting server ${server.vrn} to weight ${bes.weight}")
        LoadBalancer.setWeight(bes.serviceVrn, server.vrn, bes.weight).map {
          case true => {
            success += 1
            if (length == success) {
              originalSender ! LbSuccess
            }
          }
          case false => {
            log.error(s"Failed to update the weight of container ${server.vrn}")
            originalSender ! LbFail
          }
        }
      }
    }
  }

  private def handleSetConfigResponse( response: Future[Boolean], sender: ActorRef ) = response.map {

    case true =>
      log.info("Successfully applied new load balancer configuration")
      sender ! LbSuccess

    case false =>
      log.info("Error applying new load balancer configuration")
      sender ! LbFail
  }
}
