package actors.loadbalancer

import akka.actor.{Props, Actor, ActorLogging}

/**
 * Created by tim on 04/09/14.
 */
class LoadBalancerParentActor extends Actor with ActorLogging {

  private val lbManager = context.actorOf(Props[LoadBalancerManagerActor], "lbManager")

  def receive = {
    case m: Any => lbManager forward m
  }

}
