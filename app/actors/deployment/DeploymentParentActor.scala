package actors.deployment

import actors.deployment.scaling.{ScalingActor, SubmitInstanceScaling}
import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive
import lib.util.vamp.Number
/**
 * DeploymentParentActor functions as the supervisor of all instances of [[actors.deployment.DeploymentActor]]
 * Its most important function is to startup uniquely named actors for each deploy or undeploy action or scaling action
 * The actors names are very important, they NEED to be unique
 */

class DeploymentParentActor extends Actor with ActorLogging {

  def receive = LoggingReceive {

      // Container deployments
    case SubmitDeployment(vrn, image, service) =>

      val deploymentActor = context.actorOf(Props[DeploymentActor], s"deploy-$vrn-${Number.rnd}")
      deploymentActor forward SubmitDeployment(vrn, image, service)

      // Container undeployments
    case SubmitUnDeployment(vrn) =>

      val unDeploymentActor = context.actorOf(Props[DeploymentActor], s"undeploy-$vrn-${Number.rnd}")
      unDeploymentActor forward SubmitUnDeployment(vrn)

      // Service Deployment
    case SubmitServiceDeployment(vrn, service) =>
      val serviceDeploymentActor = context.actorOf(Props[ServiceDeploymentActor], s"service-deploy-$vrn-${Number.rnd}")
      serviceDeploymentActor forward SubmitServiceDeployment(vrn,service)

      // Scaling
    case (scale: SubmitInstanceScaling) =>
      val scalingActor = context.actorOf(Props[ScalingActor], s"container-scale-${scale.container.vrn}-${Number.rnd}")
      scalingActor forward scale
  }
}
