package actors

import akka.actor.{ActorLogging, Actor}

class LoadBalancerActor extends Actor with ActorLogging {

  def receive = {

    case "ping" => context.parent ! "pong"
  }
}
