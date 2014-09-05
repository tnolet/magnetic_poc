package actors.deployment

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive
import lib.util.vamp.Number
/**
 * DeploymentParentActor functions as the supervisor of all instances of [[actors.deployment.DeploymentActor]]
 * Its most important function is to startup uniquely named actors for each deploy or undeploy action
 * The actors names are very important, they NEED to be unique
 */

class DeploymentParentActor extends Actor with ActorLogging {

  def receive = LoggingReceive {

    case SubmitDeployment(vrn, image) =>

      val deploymentActor = context.actorOf(Props[DeploymentActor], s"deploy-$vrn-${Number.rnd}")
      deploymentActor forward SubmitDeployment(vrn,image)

    case SubmitUnDeployment(vrn) =>

      val unDeploymentActor = context.actorOf(Props[DeploymentActor], s"undeploy-$vrn-${Number.rnd}")
      unDeploymentActor forward SubmitUnDeployment(vrn)
  }
}
