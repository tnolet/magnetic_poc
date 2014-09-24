package actors.loadbalancer

import akka.actor.{ActorRef, Actor, ActorLogging}
import lib.loadbalancer.LoadBalancer
import models.docker.{DockerContainers, DockerContainerResult}
import models.loadbalancer._
import models.service.{Service, Services}
import play.api.db.slick._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play.current

sealed trait LbMessage

//Creation and Deletion messages
case class AddBackendServer( host: String, port: Int, vrn: String, backend: String) extends LbMessage
case class AddFrontend(vrn: String, port: Int) extends LbMessage
case class AddFrontendBackend(vrn: String, port: Int) extends LbMessage
case class AddBackend(vrn: String) extends LbMessage
case class RemoveBackendServer( vrn: String) extends LbMessage

//Update messages

case class UpdateBackendServerWeight(weight: Int, containerVrn: String, serviceId: Long) extends LbMessage

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
      LoadBalancer.getConfig.map { configOpt =>
        configOpt match {

          case Some(config: Configuration) => {

            log.debug("Current load balancer configuration is: " + config.toString)

            val newBackendServer = BackendServer(bes.vrn, bes.host, bes.port, 0, None, Some(false), None)
            val newConf = Configuration.addServerToBackend(config, bes.backend, newBackendServer)

            log.debug("New load balancer configuration is:" + newConf.toString)

            //Get a Future on a Boolean whether the new config was successfully applied
            val result = LoadBalancer.setConfig(newConf).map { result =>
              result match {
                case true => {
                  log.info("Successfully applied new load balancer configuration")
                  originalSender ! LbSuccess
                }
                case false => {
                  log.info("Error applying new load balancer configuration")
                  originalSender ! LbFail
                }
              }
            }
          }
          case None => {
            log.info("Loadbalancer provided an invalid configuration")
            originalSender ! LbFail
          }
        }
      }

    case RemoveBackendServer(vrn) =>

      originalSender = sender()

      log.info("Getting LB Configuration")

      // Get a Future on an Option of Config. First
      LoadBalancer.getConfig.map { configOpt =>
        configOpt match {
          case Some(config: Configuration) => {

            log.debug("Current load balancer configuration is: " + config.toString)
            val newConf = Configuration.removeServerFromBackend(config, vrn)
            log.debug("New load balancer configuration is:" + newConf.toString)

            //Get a Future on a Boolean whether the new config was successfully applied
            val result = LoadBalancer.setConfig(newConf).map { result =>
              result match {
                case true => {
                  log.info("Successfully applied new load balancer configuration")
                  originalSender ! LbSuccess
                }
                case false => {
                  log.info("Error applying new load balancer configuration")
                  originalSender ! LbFail
                }
              }
            }
          }
          case None => {
            log.info("Loadbalancer provided an invalid configuration")
            originalSender ! LbFail
          }
        }
      }

    case AddFrontendBackend(vrn,port) =>
      log.debug("Got AddFrontendBackend message")

      originalSender = sender()

      // Get a Future on an Option of Config. First
      LoadBalancer.getConfig.map { configOpt =>
        configOpt match {
          case Some(config: Configuration) => {

            log.debug("Current load balancer configuration is: " + config.toString)

            val dummyBackendServer = BackendServer("dummy-vrn", "127.0.0.1", 9999, 0, None, None, None)
            val newBackend = Backend(vrn, List(dummyBackendServer), Map("transparent" -> false))
            val newFrontend = Frontend(vrn,port,"0.0.0.0",vrn,Map("transparent" -> false))

            val _newConf = Configuration.addBackend(config, newBackend)
            val newConf = Configuration.addFrontend(_newConf, newFrontend)

            log.debug("New load balancer configuration is:" + newConf.toString)


            //Get a Future on a Boolean whether the new config was successfully applied
            LoadBalancer.setConfig(newConf).map {
                case true => {
                  log.info("Successfully applied new load balancer configuration")
                  originalSender ! LbSuccess
                }
                case false => {
                  log.info("Error applying new load balancer configuration")
                  originalSender ! LbFail
                }
            }
          }
          case None => {
            log.info("Loadbalancer provided an invalid configuration")
            originalSender ! LbFail
          }
        }
      }

    case bes : UpdateBackendServerWeight =>

      val originalSender = sender()

      DB.withSession { implicit session: Session =>

        val _service = Services.findById(bes.serviceId)

        _service match {

          case Some(srv: Service) =>

            LoadBalancer.setWeight(srv.vrn, bes.containerVrn, bes.weight).map { result =>
              result match {
                case true => { originalSender ! LbSuccess
                }
                case false => {
                  log.error(s"Failed to update the weight of container ${bes.containerVrn}")
                  originalSender ! LbFail
                }
              }
            }
          case None => originalSender ! LbNotFound
        }
    }
  }
}
