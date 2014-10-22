package actors.sla

import akka.actor.{Props, ActorLogging, Actor}
import models.Sla
import play.api.Logger

/**
 * The SlaParentActor takes care of starting up all the actor concerned with SLA management
 * It also starts up a [[ScoreBoardActor]] actor that all [[SlaCheckerActor]] actors can use to match the SLA
 * KPI's with the current scores
 */
class SlaParentActor extends Actor with ActorLogging {

  //  Create a score board manager
  private val scoreBoard = context.actorOf(Props[ScoreBoardActor], "scoreBoard")


  def receive = {
    case message: ScoreBoardMessage => scoreBoard forward message
  }

  val sla = Sla(Some(1),"rtime",30,100,5,3,3,"vrn-development-service-7797c15d.backend.rtime",1)
  Logger.info(s"Starting Sla checker for ${sla.vrn}")
  val slaChecker = context.actorOf(Props(classOf[SlaCheckerActor],sla), s"slaChecker-${sla.vrn}")
  slaChecker ! Start
}
