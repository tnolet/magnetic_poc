
import akka.actor.Props
import com.typesafe.config.ConfigFactory
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

    // start up Akka Deployment system
    DeploymentSystem.start

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
          DockerImage(Option(3L), "hello", "tnolet/hello","latest",""))
          .foreach(DockerImages.insert)
      }
    }
  }
}

object DeploymentSystem {

  import play.api.Play.current

  def start: Unit = {
    val deploymentParent = Akka.system.actorOf(
      Props(new DeploymentParentActor),
      "deploymentParent"
    )
  }
}
