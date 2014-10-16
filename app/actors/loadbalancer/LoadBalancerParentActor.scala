package actors.loadbalancer

import akka.actor.{Props, Actor, ActorLogging}

/**
 * The LoadBalancerParentActor takes care of starting up and supervising all load balancer
 * related actors
 */
class LoadBalancerParentActor extends Actor with ActorLogging {

  //  Create a load balancer manager
  private val lbManager = context.actorOf(Props[LoadBalancerManagerActor], "lbManager")

  //  Create a local proxy manager
  private val lpManager = context.actorOf(Props[LocalProxyManagerActor], "lpManager")


  def receive = {
    case message: LbMessage => lbManager forward message
    case message: LpMessage => lpManager forward message
  }

}
