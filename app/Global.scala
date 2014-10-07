
import actors.deployment.DeploymentParentActor
import actors.jobs.{CheckJobs, JobManagerActor}
import actors.loadbalancer.LoadBalancerParentActor
import akka.actor.Props
import controllers.FeedsController
import lib.discovery.MagneticServiceInstance
import lib.feeds.Feeds
import lib.marathon.Marathon
import lib.mesos.Mesos
import lib.loadbalancer.LoadBalancer

import models.docker.{DockerImages, DockerImage}
import models.service.{ServiceType, ServiceTypes}
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

    Akka.system.scheduler.schedule(0.second, 2.second, jobManager, CheckJobs)

    // Start up the Load Balancer actor system
    val lbSystem = Akka.system.actorOf(Props[LoadBalancerParentActor], name = "lbManager")

    // Start the feeds system
    val feeds = new Feeds
    feeds.startFeedsParent()

    // Start specific feeds
    feeds.startFeeds


    /**
     *
     * EXPERIMENTAL SD
     */


    val service = MagneticServiceInstance(name = "myMagneticService2", host= "local", port = 8933, vrn = "vrn-development-")
    val sd = new lib.discovery.Discovery
    sd.registerService(service)
  }

}

object InitialData {

  def insert: Unit = {
    DB.withSession{ implicit s: Session =>
      if (DockerImages.count == 0) {
        Seq(
          DockerImage(Option(1L), "mesos_test", "tnolet/mesos-tester","latest",""),
          DockerImage(Option(2L), "mesos_test", "tnolet/mesos-tester","2.0",""),
          DockerImage(Option(3L), "busybox","busybox","latest","""/bin/sh -c \"while true; do echo Hello World; sleep 4; done\""""),
          DockerImage(Option(4L), "hello", "tnolet/hello","latest",""),
          DockerImage(Option(5L), "haproxy-test", "tnolet/haproxy-rest","latest",""),
          DockerImage(Option(6L), "memchached", "sylvainlasnier/memcached","latest",""),
          DockerImage(Option(7L), "mariaDB", "paintedfox/mariadb","latest","")
        )
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
          ServiceType(id = Option(1L), name = "search", version = "1.0", mode = "http" , basePort = 21000),
          ServiceType(id = Option(2L), name = "search", version = "2.0", mode = "http" , basePort = 21000),
          ServiceType(id = Option(3L), name = "cart", version = "1.0", mode = "http" , basePort = 22000),
          ServiceType(id = Option(4L), name = "cache", version = "1.0", mode = "tcp" , basePort = 11211),
          ServiceType(id = Option(5L), name = "database", version = "1.0", mode = "tcp" , basePort = 23306)
        ).foreach(ServiceTypes.insert)
      }
    }
  }
}

