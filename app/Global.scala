
import akka.actor.Props
import com.typesafe.config.ConfigFactory
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
    InitialData.insert()

    // Check Mesos Master health

    val healthy = Mesos.Health

    healthy.onComplete({
      case Success(int) => Logger.info("Mesos healthy")
      case Failure(exception) => Logger.info("Mesos not healthy")
    })


//    val conf = ConfigFactory.load()
//    val dockerHost = conf.getString("docker.daemon.host")
//    val dockerPort = conf.getInt("docker.daemon.port")
    import play.api.Play.current

    val lbParentActor = Akka.system.actorOf(
      Props(new LoadBalancerParentActor),
      "lbParentActor"
    )
  }
}

object InitialData {

  def insert(): Unit = {
    DB.withSession{ implicit s: Session =>
      if (DockerImages.count == 0) {
        Seq(
          DockerImage(Option(1L), "tnolet/mesos-tester","latest",""),
          DockerImage(Option(2L), "busybox","latest","/bin/sh -c \"while true; do echo Hello World; sleep 4; done\""),
          DockerImage(Option(3L), "tnolet/hello","latest",""))
          .foreach(DockerImages.insert)
      }
    }
  }
}

