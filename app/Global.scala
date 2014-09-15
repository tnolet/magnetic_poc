
import actors.deployment.DeploymentParentActor
import actors.jobs.{CheckJobs, JobManagerActor}
import actors.loadbalancer.LoadBalancerParentActor
import akka.actor.Props
import lib.marathon.Marathon
import lib.mesos.Mesos
import lib.loadbalancer.LoadBalancer

import models.docker.{DockerImages, DockerImage}
import models.service.{Services, Service, ServiceType, ServiceTypes}
import play.api._
import play.api.libs.concurrent.Akka
import models._
import play.api.db.slick._
import play.api.Play.current
import scala.util.{Success, Failure}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


object Global extends GlobalSettings {

  override def onStart (application: Application): Unit = {

    /**********************************************
      *
      *  Initial insertion of test data
      *
      **********************************************/

    InitialData.insert

    /**********************************************
     *
     *  Initial health checks for external dependencies
     *
     **********************************************/

    // Check Mesos Master health
    val healthyMesos = Mesos.Health

    healthyMesos.onComplete({
      case Success(returnCode) =>
        if (returnCode < 399) {Logger.info(s"Successfully connected to Mesos on ${Mesos.uri}")}
        else {Logger.error("Mesos is running, but is not healthty") }

      case Failure(exception) => Logger.info(s"Could not connect to Mesos on ${Mesos.uri}")
    })

    // Check Marathon health
    val healthyMarathon = Marathon.Health

    healthyMarathon.onComplete({
      case Success(returnCode) =>
      if (returnCode < 399)
        { Logger.info(s"Successfully connected to Marathon on ${Marathon.uri}")}
      else
        { Logger.error("Marathon is running, but is not healthy")}

      case Failure(exception) => Logger.info(s"Could not connect to Marathon on ${Marathon.uri}")
    })

    // Check load balancer health
    val healthyLoadBalancer = LoadBalancer.Health

    healthyLoadBalancer.onComplete({
      case Success(returnCode) =>
        if (returnCode < 399)
        { Logger.info(s"Successfully connected to the load balancer on ${LoadBalancer.uri}")}
        else
        { Logger.error("Load balancer is running, but is not healthy")}

      case Failure(exception) => Logger.info(s"Could not connect to the load balancer on ${LoadBalancer.uri}")
    })

    /**********************************************
      *
      *  Start up Akka Systems
      *
      **********************************************/

    // Start up a Deployment actor system with a parent at the top
    Akka.system.actorOf(Props[DeploymentParentActor], "deployer")

    // Start up the JobManager actor system
    val jobManager = Akka.system.actorOf(Props[JobManagerActor], name = "jobManager")

    Akka.system.scheduler.schedule(0.second, 5.second, jobManager, CheckJobs)


    // Start up the Load Balanacer actor system
    Akka.system.actorOf(Props[LoadBalancerParentActor], name = "lbManager")

  }
}

object InitialData {

  def insert: Unit = {
    DB.withSession{ implicit s: Session =>
      if (DockerImages.count == 0) {
        Seq(
          DockerImage(Option(1L), "mesos_test", "tnolet/mesos-tester","latest",""),
          DockerImage(Option(2L), "busybox","busybox","latest","""/bin/sh -c \"while true; do echo Hello World; sleep 4; done\""""),
          DockerImage(Option(3L), "hello", "tnolet/hello","latest",""),
          DockerImage(Option(3L), "haproxy-test", "tnolet/haproxy-rest","latest",""))
          .foreach(DockerImages.insert)
      }
      if (Environments.count == 0) {
        Seq(
          Environment(Option(1L), "development", "created"),
          Environment(Option(2L), "test1", "created"),
          Environment(Option(3L), "test2", "created"),
          Environment(Option(4L), "acceptance1", "created"),
          Environment(Option(5L), "acceptance2", "created"),
          Environment(Option(6L), "pre-production", "created"),
          Environment(Option(7L), "production", "created"),
          Environment(Option(8L), "disaster recovery", "created"))
          .foreach(Environments.insert)
      }
      if (ServiceTypes.count == 0 ) {
        Seq(
          ServiceType(Option(1L),"search","1.0"),
          ServiceType(Option(2L),"search","2.0"),
          ServiceType(Option(3L),"cart","1.1")
        ).foreach(ServiceTypes.insert)
      }
      if (Services.count == 0) {
        Seq(
          Service(Option(1L),8900,"initial",lib.util.vamp.Naming.createVrn("service","development"),1,1),
          Service(Option(1L),8901,"initial",lib.util.vamp.Naming.createVrn("service","development"),1,2),
          Service(Option(1L),8902,"initial",lib.util.vamp.Naming.createVrn("service","test1"),2,1),
          Service(Option(1L),8903,"initial",lib.util.vamp.Naming.createVrn("service","test2"),2,3)
        ).foreach(Services.insert)
      }
    }
  }
}

