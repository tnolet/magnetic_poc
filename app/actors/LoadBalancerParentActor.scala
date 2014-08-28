package actors

import akka.actor.{Props, ActorLogging, Actor}
import play.api.libs.concurrent.Akka
import scala.concurrent.ExecutionContext.Implicits.global

class LoadBalancerParentActor extends Actor with ActorLogging {

  import play.api.Play.current
  import scala.concurrent.duration._

  val lbActor = context.actorOf(Props[LoadBalancerActor], "lbActor")

  Akka.system.scheduler.schedule(
    0.seconds, 30.seconds, lbActor, "ping"
  )


  def receive = {

    case "pong" => {
      log.info("Child is responding")
    }
  }
}
