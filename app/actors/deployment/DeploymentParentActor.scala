package actors.deployment

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive

/**
 * DeploymentParentActor functions as the supervisor of all instances of [[actors.deployment.DeploymentActor]]
 */

class DeploymentParentActor extends Actor with ActorLogging {

  def receive = LoggingReceive {

    case Submit(jobId, image) =>
      val deploymentActor = context.actorOf(Props[DeploymentActor], s"${jobId}_${image.id.get}_${image.version}")
      deploymentActor forward Submit(jobId,image)
  }
}
