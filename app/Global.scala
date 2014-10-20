
import actors.deployment.DeploymentParentActor
import actors.jobs.{CheckJobs, JobManagerActor}
import actors.loadbalancer.LoadBalancerParentActor
import akka.actor.Props
import lib.kairosdb.KairosDB
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

    //Check KairosDB health
    val healthyKairosDB = KairosDB.Health

    healthyKairosDB.onComplete({
      case Success(returnCode) =>
        if(returnCode < 399)
        { Logger.info(s"Successfully connected to the Kairos DB on ${KairosDB.uri}")}
        else
        { Logger.error("KairosDB is running, but is not healthy")}
      case Failure(exception) => Logger.info(s"Could not connect to the KairosDB on ${KairosDB.uri}")

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


  }
}

object InitialData {

  def insert: Unit = {
    DB.withSession{ implicit s: Session =>
      if (DockerImages.count == 0) {
        Seq(
          DockerImage(Option(1L), "mesos_test", "tnolet/mesos-tester","latest",0,"http",""),
          DockerImage(Option(2L), "mesos_test", "tnolet/mesos-tester","2.0",0,"http",""),
          DockerImage(Option(2L), "test_shop", "tnolet/test-shop","0.2",80,"tcp","./start.sh"),
          DockerImage(Option(3L), "busybox","busybox","latest",0,"http","""/bin/sh -c \"while true; do echo Hello World; sleep 4; done\""""),
          DockerImage(Option(4L), "hello", "tnolet/hello","latest",0,"http",""),
          DockerImage(Option(5L), "haproxy-test", "tnolet/haproxy-rest","latest",0,"http",""),
          DockerImage(Option(6L), "memcached", "sylvainlasnier/memcached","latest",11211,"tcp",""),
          DockerImage(Option(7L), "mariaDB", "paintedfox/mariadb","latest",3306,"tcp","")
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
          ServiceType(id = Option(2L), name = "search", version = "2.0", mode = "http" , basePort = 21500),
          ServiceType(id = Option(3L), name = "cart", version = "1.0", mode = "http" , basePort = 22000),
          ServiceType(id = Option(3L), name = "shop", version = "1.0", mode = "http" , basePort = 22500),
          ServiceType(id = Option(4L), name = "cache", version = "1.0", mode = "tcp" , basePort = 11211),
          ServiceType(id = Option(5L), name = "database", version = "1.0", mode = "tcp" , basePort = 23306),
          ServiceType(id = Option(6L), name = "localproxy", version = "1.0", mode = "htpp" , basePort = 10002, systemService = true),
          ServiceType(id = Option(7L), name = "loadbalancer", version = "1.0", mode = "http" , basePort = 10001, systemService = true)
        ).foreach(ServiceTypes.insert)
      }
    }
  }
}

