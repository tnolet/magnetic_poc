package actors

import akka.actor.{ActorLogging, Actor}
import akka.event.LoggingReceive

class DeploymentActor extends Actor with ActorLogging {

  override def preStart = {
    log.info("Started deployment")
  }

  def receive = LoggingReceive {
    case "ping" => context.parent ! "pong"
  }
}
