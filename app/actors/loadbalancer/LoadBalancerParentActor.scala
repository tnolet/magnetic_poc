package actors.loadbalancer

import akka.actor.{Props, Actor, ActorLogging}

/**
 * The LoadBalancerParentActor takes care of starting up and supervising all load balancer
 * related actors
 */
class LoadBalancerParentActor extends Actor with ActorLogging {

  //  Create a load balancer manager
  private val lbManager = context.actorOf(Props[LoadBalancerManagerActor], "lbManager")

  // Create a load balancer metric feed actor
  private val lbMetrics = context.actorOf(Props[LoadBalancerMetricsActor], "lbMetrics")
  lbMetrics ! Start

  def receive = {
    case message: LbMessage => lbManager forward message
    case message: LbMetricsMessage => lbMetrics forward message
  }

}
