
import akka.actor.Props
import lib.marathon.Marathon
import play.api._
import play.api.libs.concurrent.Akka
import actors._
import models._
import play.api.db.slick._
import play.api.Play.current
import lib.mesos.Mesos
import scala.util.{Success, Failure}
import scala.concurrent.ExecutionContext.Implicits.global


object Global extends GlobalSettings {

  override def onStart (application: Application): Unit = {

    // Some Mock data for initial testing
    InitialData.insert


    // Check Mesos Master health
    val healthyMesos = Mesos.Health

    healthyMesos.onComplete({
      case Success(returnCode) =>
        if (returnCode < 399) {Logger.info(s"Successfully connected to Mesos on ${Mesos.uri}")}
        else {Logger.error("Mesos is running, but is not healthty") }

      case Failure(exception) => Logger.info(s"Could not connect to Mesos on ${Mesos.uri}")
    })

    // Check Marathon health
    val healthyMarathon = Mesos.Health


    healthyMarathon.onComplete({
      case Success(returnCode) =>
      if (returnCode < 399)
        { Logger.info(s"Successfully connected to Marathon on ${Marathon.uri}")}
      else
        { Logger.error("Marathon is running, but is not healthy")}

      case Failure(exception) => Logger.info(s"Could not connect to Marathon on ${Marathon.uri}")
    })

    // Start up a Deployment actor system with a parent at the top
    val deployer = Akka.system.actorOf(Props[DeploymentParentActor], "deployer")



    //    val conf = ConfigFactory.load()
//    val dockerHost = conf.getString("docker.daemon.host")
//    val dockerPort = conf.getInt("docker.daemon.port")

  }
}

object InitialData {

  def insert: Unit = {
    DB.withSession{ implicit s: Session =>
      if (DockerImages.count == 0) {
        Seq(
          DockerImage(Option(1L), "mesos_test", "tnolet/mesos-tester","latest",""),
          DockerImage(Option(2L), "busybox","busybox","latest","/bin/sh -c \"while true; do echo Hello World; sleep 4; done\""),
          DockerImage(Option(3L), "hello", "tnolet/hello","latest",""),
          DockerImage(Option(3L), "hello", "tnolet/haproxy-rest","latest","-port=$PORT0"))
          .foreach(DockerImages.insert)
      }
    }
  }
}

