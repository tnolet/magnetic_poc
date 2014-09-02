package actors

import akka.actor.{Props, ActorLogging, Actor}
import akka.event.LoggingReceive

sealed trait Message

trait DeployMessage extends Message
case class Stage(ImageId: Long) extends DeployMessage

class DeploymentParentActor extends Actor with ActorLogging {

  def receive = LoggingReceive {

    case Stage(id: Long) =>
      val deploymentActor = context.actorOf(Props[DeploymentActor], s"$id")
      log.info(s"Created actor: $deploymentActor")
      sender ! deploymentActor.hashCode().toString
  }
}
