package actors.loadbalancer

import akka.actor.{ActorRef, Actor, ActorLogging}
import lib.loadbalancer.LoadBalancer
import models.loadbalancer._
import scala.concurrent.ExecutionContext.Implicits.global

sealed trait LbMessage
case class AddBackendServer( host: String, port: Int, vrn: String, backend: String) extends LbMessage
case class AddFrontend(vrn: String, port: Int) extends LbMessage
case class AddFrontendBackend(vrn: String, port: Int) extends LbMessage
case class AddBackend(vrn: String) extends LbMessage
case class RemoveBackendServer( vrn: String) extends LbMessage
case object LbSuccess extends LbMessage
case object LbFail extends LbMessage

class LoadBalancerManagerActor extends Actor with ActorLogging {

  private var config: Configuration = _
  private var originalSender: ActorRef = _

  def receive =  {

    case AddBackendServer(host,port,vrn,service) =>

      originalSender = sender()
      log.info("Getting LB Configuration")

      // Get a Future on an Option of Config
      LoadBalancer.getConfig.map { configOpt =>
        configOpt match {

          case Some(config: Configuration) => {

            log.debug("Current load balancer configuration is: " + config.toString)

            val newBackendServer = BackendServer(vrn, host, port, 0, None, None, None)
            //val newBackend = Backend(vrn, List(newBackendServer), Map("transparent" -> false))
            val newConf = Configuration.addServerToBackend(config,service,newBackendServer)

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
  }
}
