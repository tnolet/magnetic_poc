package actors.deployment

import actors.jobs.{UpdateJob, addJobEvent}
import actors.loadbalancer.{LbFail, LbSuccess, AddFrontendBackend}
import akka.actor.{ActorRef, Actor, LoggingFSM}
import models.Jobs
import models.service.{Services, ServiceCreate}
import play.api.db.slick._
import play.api.Play.current


//events for deploying
case class SubmitServiceDeployment(vrn: String, service: ServiceCreate) extends DeployEvent


//states for deploying
case class ServiceState(status: String) extends Data


class ServiceDeploymentActor extends Actor with LoggingFSM[DeployState,Data] {

  private var jobExecutor: ActorRef = _
  private var vrn: String = _
  private var port: Int = _
  private var eventType: String = _

  val lbManager = context.actorSelection("akka://application/user/lbManager")


  startWith(Idle, Uninitialized)

  // Initial message for starting a deployment
  when(Idle) {

    // Initial message for starting a deployment
    case Event(SubmitServiceDeployment(_vrn, _service), Uninitialized) =>

      // Set all variables for the service we are going to deploy

      jobExecutor = sender()
      vrn = _vrn
      port = _service.port
      eventType = "serviceDeployment"

      log.info(s"Staging deployment of service with unique ID $vrn")

      val newState = new ServiceState("STAGING")

      goto(Staging) using newState
  }

  when(Staging) {

    case Event(RunExpose, s: ServiceState) =>

      val newStateData = s.copy("EXPOSING")
      goto(WaitingExposure) using newStateData

    case Event(Fail,f: Failure) =>
      goto(Failed)

  }

  when(WaitingExposure) {

    case Event(LbSuccess, s: ServiceState) =>
      val newStateData = s.copy("LIVE")
      goto(Live) using newStateData

    case Event(LbFail, s: ServiceState) =>
      val newStateData = s.copy("FAILED_EXPOSURE")
      goto(Failed) using newStateData

    case Event(Fail, f: Failure) =>
      goto(Failed)
  }

  when(Live) {

    case Event(_,_) =>

    stay()

    case Event(Fail,f: Failure) =>
      goto(Failed)

  }

  /**
   *
   * Failed and unhandled states
   *
   */

  when(Failed){
    case Event(_,_) =>

      stay()
  }

  whenUnhandled {
    case Event(e, s) =>
      log.warning("Received unhandled request {} in state {}/{}", e, stateName, s)
      stay()
  }

  /**
   *
   * Transitions
   *
   */
  onTransition {


    /**
     * Add the service as haproxy backend and frontend to the load balancer, in that order
     */
    case Idle -> Staging =>
    nextStateData match {
      case ServiceState(state) =>

        sendStateUpdate(state)

        // Todo: merge both actions one
        // Todo: create more intermediate states in case of failure or port/naming collisions
        log.debug("Requesting update of load balancer")
        lbManager ! AddFrontendBackend(vrn,port)

        self ! RunExpose
    }

    case Staging -> WaitingExposure =>
      nextStateData match {
        case ServiceState(state) =>

          sendStateUpdate(state)
      }

    case WaitingExposure -> Live =>
      nextStateData match {
        case ServiceState(state) =>

          sendStateUpdate(state)

          jobExecutor ! UpdateJob(Jobs.status("finished"))

      }

    case _ -> Failed =>
      nextStateData match {
        case ServiceState(state) =>

          sendStateUpdate(state)

          jobExecutor ! UpdateJob(Jobs.status("failed"))
      }

  }

  initialize()

  /**
   *
   * Updates the state of all who want to know. In this case specifically the service and the job
   * by the means of job events.
   *
   * @param state the current state of the machine. The evenType is a local var
   */

  def sendStateUpdate(state: String) : Unit = {

    // Update the job events

    jobExecutor ! addJobEvent(state, eventType)

    // Update the container
    DB.withSession { implicit session: Session =>
      Services.updateStateByVrn(vrn,state)
    }

  }
}
