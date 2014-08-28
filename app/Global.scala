
import akka.actor.Props
import com.typesafe.config.ConfigFactory
import play.api._
import play.api.libs.concurrent.Akka
import actors._
import models._
import play.api.db.slick._
import play.api.Play.current


object Global extends GlobalSettings {

  override def onStart (application: Application): Unit = {

   InitialData.insert()

    val conf = ConfigFactory.load()
    val docker_host = conf.getString("docker.daemon.host")
    val docker_port = conf.getInt("docker.daemon.port")

    import play.api.Play.current

    val lbParentActor = Akka.system.actorOf(
      Props(new LoadBalancerParentActor),
      "lbParentActor"
    )
  }
}

// Some Mock data for initial testing
object InitialData {

  def insert(): Unit = {
    DB.withSession{ implicit s: Session =>
      if (DockerImages.count == 0) {
        Seq(
          DockerImage(Option(1L), "Redis","1.0"),
          DockerImage(Option(2L), "Redis","2.0"),
          DockerImage(Option(3L), "SQL","0.8"),
          DockerImage(Option(4L), "SQL","0.8.1"),
          DockerImage(Option(5L), "Web","0.5.1"),
          DockerImage(Option(6L), "Web","0.9"),
          DockerImage(Option(7L), "Core","0.6"),
          DockerImage(Option(8L), "Core","3.0"),
          DockerImage(Option(9L), "Web","1.0")).foreach(DockerImages.insert)
      }
    }
  }
}

