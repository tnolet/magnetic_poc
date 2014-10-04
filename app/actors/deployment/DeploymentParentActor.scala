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
    case (deploy: SubmitDeployment) =>

      val deploymentActor = context.actorOf(Props[DeploymentActor], s"deploy-${deploy.vrn}-${Number.rnd}")
      deploymentActor forward deploy

      // Container undeployments
    case (unDeploy: SubmitUnDeployment) =>

      val unDeploymentActor = context.actorOf(Props[DeploymentActor], s"undeploy-${unDeploy.vrn}-${Number.rnd}")
      unDeploymentActor forward unDeploy

      // Service Deployment
    case deploy: SubmitServiceDeployment =>
      val serviceDeploymentActor = context.actorOf(Props[ServiceDeploymentActor], s"service-deploy-${deploy.vrn}-${Number.rnd}")
      serviceDeploymentActor forward deploy

    // Service Deployment
    case unDeploy: SubmitServiceUnDeployment =>
      val serviceDeploymentActor = context.actorOf(Props[ServiceDeploymentActor], s"service-deploy-${unDeploy.vrn}-${Number.rnd}")
      serviceDeploymentActor forward unDeploy

      // Scaling
    case (scale: SubmitInstanceScaling) =>
      val scalingActor = context.actorOf(Props[ScalingActor], s"container-scale-${scale.container.vrn}-${Number.rnd}")
      scalingActor forward scale
  }
}
