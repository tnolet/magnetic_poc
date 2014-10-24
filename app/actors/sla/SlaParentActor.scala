package actors.sla


import akka.actor._
import models.{SlaState, Slas}
import play.api.db.slick._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

case object CheckForNewSla

/**
 * The SlaParentActor takes care of starting up all the actor concerned with SLA management
 * It also starts up a [[ScoreBoardActor]] actor that all [[SlaCheckerActor]] actors can use to match the SLA
 * KPI's with the current scores
 */
class SlaParentActor extends Actor with ActorLogging {

  import play.api.Play.current

  //  Create a score board manager
  private val scoreBoard = context.actorOf(Props[ScoreBoardActor], "scoreBoard")

  def receive = {


    // All SLA's are check for their State. Any state except for the DESTROYED state should result in a running SLA.
    // This ensures that SLA's survive the restart of the application.

    case CheckForNewSla =>
      DB.withSession { implicit session: Session =>
        Slas
          .all
          .foreach( sla  => {
          sla.state match {

            case "DESTROYED" =>

              log.info(s"Destroying Sla checker for ${sla.vrn}")

              context.child(s"slaChecker-${sla.vrn}").map {

                case actor =>
                  actor ! PoisonPill
                  Slas.delete(sla.id.get)
              }

            case _ =>

              implicit val timeout = akka.util.Timeout(5 seconds) // Timeout for the resolveOne call

              context.actorSelection(s"slaChecker-${sla.vrn}").resolveOne().onComplete{
                case actor : Try[ActorRef] =>
                  if (actor.isFailure) {

                    log.info(s"Starting Sla checker for ${sla.vrn}")

                    val slaChecker = context.actorOf(Props(classOf[SlaCheckerActor], sla), s"slaChecker-${sla.vrn}")
                    slaChecker ! Start
                    DB.withSession { implicit session: Session =>
                      Slas.update_state(sla.id.get, SlaState.ACTIVE)
                    }
                  }
                  if (actor.isSuccess) {
                    log.debug(s"Sla checker for ${sla.vrn} is running")
                  }
              }
          }
        })
      }

    case message: ScoreBoardMessage => scoreBoard forward message

  }
}
