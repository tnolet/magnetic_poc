package actors.sla

import akka.actor._
import models.docker.{ContainerInstances, DockerContainers, DockerContainer}
import models.service.Services
import models.{SlaState, Sla}
import play.api.db.slick._
import scala.concurrent.duration._
import lib.job.{ScaleJobBuilder, Horizontal}

/**
 * The SlaCheckerActor checks the rules of a Sla against the score board
 */

trait SlaMessage

// Events to send or respond to
case object CheckSla extends SlaMessage
case object Start extends SlaMessage
case object Ok extends SlaMessage
case object NotOk extends SlaMessage
case object IssuedCommand extends SlaMessage
case object FinishedCommand extends SlaMessage
case object Invalid extends SlaMessage


// possible states
trait SlaFSMState

case object Idle extends SlaFSMState
case object GettingScore extends SlaFSMState
case object Checking extends SlaFSMState
case object OkWait extends SlaFSMState
case object NotOkWait extends SlaFSMState
case object InvalidMeasurement extends SlaFSMState
case object Escalating extends SlaFSMState
case object DeEscalating extends SlaFSMState
case object CommandWait extends SlaFSMState
case object CommandDone extends SlaFSMState
case object Evaluating extends SlaFSMState
case object Failed extends SlaFSMState

trait Data
case object Uninitialized extends Data
case class SlaData(stage: Int = 0, score: Long = -1, escalations : Int = 0) extends Data



class SlaCheckerActor(_sla: Sla) extends Actor with  LoggingFSM[SlaFSMState, Data] {

  import play.api.Play.current

  val scoreBoard = context.actorSelection("../scoreBoard")
  val deployer = context.actorSelection("/user/deployer")

  // store the sla and some other stuff as local variables
  private val sla = _sla
  private var originalSender: ActorRef = _

  private val checkInterval = 5.seconds
  private val evaluationInterval = 30.seconds
  private val retryFailedInterval = 30.seconds


  startWith(Idle, Uninitialized)

  when(Idle) {


    // initial message to  start the whole Sla checking machine
    case Event(Start, Uninitialized) =>

      originalSender = sender()
      log.info(s"Started Sla checker for  on ${sla.vrn}")

      val data = SlaData()
      goto(GettingScore) using data

  }

  when(GettingScore) {

    case Event(score: Score, d: SlaData) =>

      val data = d.copy( score = score.value)
      goto(Checking) using data

  }

  when(Checking) {

    // when we receive an Ok event, we lower the back off stage with 1, because an Ok score lowers the escalation stage
    // Then we move to the OkWait
    case Event(Ok,d: SlaData) =>

      var newStage : Int = 0
       if (d.stage !=  0 ) {
         newStage = d.stage -1
       }

      val data = d.copy(stage = newStage)
      goto(OkWait) using data

      // When we receive a NotOK event, we start counting down back off stages and
      // waiting for a specific amount of back off time. If all stages have passed, we go to
      // the Escalating stage, when we have not used up the maximum amount of escalations
    case Event(NotOk, d: SlaData) =>

        // we have reached the max back off stages and have room to escalate
      if (d.stage == sla.backOffStages && d.escalations < sla.maxEscalations) {

        val data = d.copy()
        goto(Escalating) using data
      }

        // we have reached the maximum amount of escalations...we fail the SLA
      else if (d.stage == sla.backOffStages && d.escalations == sla.maxEscalations)

      {
        val data = d.copy()
        goto(Failed) using data

      } else {

        val data = d.copy( stage = d.stage + 1)

        goto(NotOkWait) using data
      }


    case Event(Invalid, d: SlaData) =>
      val data = d.copy()
      goto(InvalidMeasurement) using data

  }

  when(OkWait, stateTimeout = checkInterval) {

    case Event(StateTimeout, d: SlaData) =>
      val data = d.copy()
      goto(GettingScore) using data

    case Event(_,_) =>
      stay()

  }

  // When we are in the NotOkWait stage, we wait for a re check based on the backoff time
  when(NotOkWait, stateTimeout = sla.backOffTime seconds) {

    case Event(StateTimeout, d: SlaData) =>
      val data = d.copy()
      goto(GettingScore) using data

    case Event(_,_) =>
      stay()

  }

  when(InvalidMeasurement, stateTimeout = checkInterval) {

    case Event(StateTimeout, d: SlaData) =>
      val data = d.copy()
      goto(GettingScore) using data

    case Event(_,_) =>
      stay()

  }

  // on escalation, reset the back off stages and up the number of escalations
  when(Escalating) {

    case Event(IssuedCommand,d: SlaData) =>
      val data = d.copy(stage = 0, escalations = d.escalations + 1)
      goto(CommandWait) using data

  }

  // In this state, we are waiting for the command triggered by the escalation to finish
  when(CommandWait) {

    case Event(FinishedCommand,d: SlaData) =>
      val data = d.copy()
      goto(Evaluating) using data

  }

  // In this state, we are evaluating whether a performed command had impact on the score
  when(Evaluating, stateTimeout = evaluationInterval) {

    case Event(StateTimeout, d: SlaData) =>
      val data = d.copy()
      goto(GettingScore) using data

  }

  when(Failed, stateTimeout = retryFailedInterval) {

    case Event(StateTimeout, d: SlaData) =>
      val data = d.copy()
      goto(GettingScore) using data

  }

  /**
   *
   * Transitions
   *
   */

  onTransition {

    // Start of with getting the first score
    case _ -> GettingScore =>
      nextStateData match {
        case s: SlaData =>

          // ask the score board for the score of the metric we are interested in
          log.info(s"Getting score for: ${sla.vrn}.${sla.metricType}" )
          scoreBoard ! GetScore(metric = s"${sla.vrn}.${sla.metricType}")

      }

    /*
            When we have a score, we check it against the thresholds in the sla.
            If necessary we escalate, if not, we move to Ok and do it again

             */


    case GettingScore -> Checking =>

      nextStateData match {
        case s: SlaData =>
          val message = checkScore(s.score, sla, s)

          self ! message
      }

    case Checking -> OkWait =>

      nextStateData match {
        case s: SlaData =>
          sendStateUpdate(SlaState.OK,s.stage, s.escalations)

      }

    case Checking -> NotOkWait =>

      nextStateData match {
        case s: SlaData =>
          log.info(s"${sla.vrn}: Back off staged triggered. Now at stage ${s.stage} of ${sla.backOffStages} ")
          sendStateUpdate(SlaState.WARNING,s.stage, s.escalations)

      }

    case Checking -> Escalating =>

      nextStateData match {
        case s: SlaData =>

          createScalingJob(sla.vrn)
          self ! IssuedCommand
      }

    case Escalating -> CommandWait =>

      nextStateData match {
        case s: SlaData =>

          log.info(s"${sla.vrn}: Escalation triggered. Issued scaling job.")
          sendStateUpdate(SlaState.ESCALATED, s.stage, s.escalations)
          log.info(s"${sla.vrn}: Command triggered. Should check for successful completion of some command now")
          self ! FinishedCommand
      }

    case CommandDone -> Evaluating =>

      nextStateData match {
        case s: SlaData =>

          log.info(s"${sla.vrn}: Evaluating the effects of the escalation command for $evaluationInterval seconds")
          self ! FinishedCommand
      }

    case _ -> Failed =>

      nextStateData match {
        case s: SlaData =>

          if (s.escalations >= sla.maxEscalations) {

            log.info(s"${sla.vrn}: SLA failed: Escalation needed, but reached maximum number of ${sla.maxEscalations}. Retrying in $retryFailedInterval seconds.")
            sendStateUpdate(SlaState.FAILED, s.stage, s.escalations)
          }
      }
  }

  initialize()

  /**
   * Checks the score against the SLA and returns a message we can send into the state machine again
   * @param score a score the score from the scoreboard
   * @param sla a sla of the type [[models.Sla]]
   * @return a message of the type [[SlaMessage]]
   */
  private def checkScore(score: Long, sla: Sla, s: SlaData) : SlaMessage = {

    score match {

      case -1 =>
        log.info(s"${sla.vrn}: Found invalid score of $score, moving on... =>  Stage: ${s.stage} Esc: ${s.escalations}")
        Invalid
      case  x : Long if x > sla.highThreshold =>
        log.info(s"${sla.vrn}: Score  $score is higher than SLA high threshold ${sla.highThreshold} => Stage: ${s.stage} Esc: ${s.escalations}")
        NotOk

      case  x : Long if x <= sla.lowThreshold =>
        log.info(s"${sla.vrn}: Score  $score is lower than or equal to SLA low threshold ${sla.lowThreshold} => Stage: ${s.stage} Esc: ${s.escalations}")
        Ok

      case x : Long if x > sla.lowThreshold && x < sla.highThreshold =>
        log.info(s"${sla.vrn}: Score  $score is within a safe margin of the SLA threshold => Stage: ${s.stage} Esc: ${s.escalations}")
        Ok

      case _ =>
        log.info(s"${sla.vrn}: Cannot read score, moving on... => Stage: ${s.stage} Esc: ${s.escalations}")
        Invalid
    }
  }

  def sendStateUpdate(state: SlaState.State, currentStage: Int, escalations: Int) : Unit = {

    // Update the container
    DB.withSession { implicit session: Session =>
      models.Slas.update_state_by_vrn(sla.vrn,state, currentStage, escalations)
    }

  }

  /**
   * Gets the container and current amount of instances and submits a scaling job. Notice the +1!
   * @param vrn
   * @return the id of the scaling Job
   */
  def createScalingJob(vrn: String) : Option[Long]  = {

    DB.withSession { implicit session: Session =>


        Services.findByVrn(vrn).map { srv =>

          val container = DockerContainers.findByServiceId(srv.id.get).filter( cnt => cnt.state == "LIVE").toList.head
          log.info(s"Scaling up container instances for container: ${container.vrn}")
          val currentInstanceAmount = ContainerInstances.findByContainerId(container.id.get).length

          // create a scaleJob
          val builder = new lib.job.ScaleJobBuilder
          val scaleType = Horizontal(vrn, container, currentInstanceAmount + 1)

          builder.setScaleType(scaleType)
          val jobId = builder.build
          jobId
          }
      }
    }
}



