package actors.loadbalancer

import akka.actor.{Actor, ActorLogging}
import lib.loadbalancer.LoadBalancer
import models.loadbalancer._
import scala.concurrent.ExecutionContext.Implicits.global

sealed trait LbMessage
case class AddBackendServer( host: String, port: Int, appId: String) extends LbMessage

class LoadBalancerManagerActor extends Actor with ActorLogging {

  private var config: Configuration = _

  def receive =  {
    case AddBackendServer(host,port,appId) =>

      log.info("Getting LB Configuration")

      LoadBalancer.getConfig.map { config =>


        log.debug("Current load balancer configuration is: " + config.toString)
        val newBackendServer = BackendServer(appId + host, host, port, 0, None, None, None)
        val newBackend = Backend(appId, List(newBackendServer), Map("transparent" -> false ))
        val newConf = Configuration.addBackend(config, newBackend)
        log.debug("New load balancer configuration is:" + newConf.toString)
        LoadBalancer.setConfig(newConf)
      }


  }
}
