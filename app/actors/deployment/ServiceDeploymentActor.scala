package actors.deployment

import actors.jobs.{UpdateJob, addJobEvent}
import actors.loadbalancer._
import akka.actor.{ActorRef, Actor, LoggingFSM}
import lib.job.UnDeploymentJobBuilder
import models.Jobs
import models.docker.DockerContainers
import models.service.{ServiceType, Service, Services}
import play.api.db.slick._
import play.api.Play.current



//events for deploying
case class SubmitServiceDeployment(servType: String, vrn: String, port: Int, mode: String) extends DeployEvent

// events for undeploying
case class SubmitServiceUnDeployment(servType: ServiceType, service: Service) extends DeployEvent

//states data
case class ServiceState(status: String) extends Data


class ServiceDeploymentActor extends Actor with LoggingFSM[DeployState,Data] {

  private var jobExecutor: ActorRef = _
  private var servType: String = _
  private var vrn: String = _
  private var port: Int = _
  private var mode: String = _
  private var eventType: String = _
  private var service : Service = _


  val lbManager = context.actorSelection("akka://application/user/lbManager")


  startWith(Idle, Uninitialized)

  // Initial message for starting a deployment
  when(Idle) {

    // Initial message for starting a deployment
    case Event(deploy: SubmitServiceDeployment, Uninitialized) =>

      // Set all variables for the service we are going to deploy

      jobExecutor = sender()
      vrn = deploy.vrn
      port = deploy.port
      mode = deploy.mode
      servType = deploy.servType
      eventType = "serviceDeployment"

      log.info(s"Staging deployment of service $vrn")

      val newState = new ServiceState("STAGING")

      goto(Staging) using newState


    case Event(unDeploy: SubmitServiceUnDeployment, Uninitialized) =>

      jobExecutor = sender()
      service = unDeploy.service
      vrn = unDeploy.service.vrn
      servType = unDeploy.servType.name
      eventType = "serviceUnDeployment"


      log.info(s"Starting the undeployment of service $vrn")

      val newState = new ServiceState("WAITING_FOR_UNEXPOSE")

      goto(WaitingUnExpose) using newState

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
   * undeploy states
   *
   */

  when(WaitingUnExpose){

    case Event(LbSuccess, s: ServiceState) =>
      val newStateData = s.copy("UNEXPOSED")
      goto(WaitingDestroy) using newStateData

    case Event(LbFail, s: ServiceState) =>
      val newStateData = s.copy("LIVE_WITH_FAILED_UNEXPOSURE")
      goto(Failed) using newStateData

    case Event(Fail, f: Failure) =>
      goto(Failed)
  }

    /**
     *   When in the WaitingDestroy stage, we are effectively waiting for updates from undeployment jobs
     */
  when(WaitingDestroy){


    case Event(DestroyFinish, s: ServiceState) =>

      val newStateData = s.copy("DESTROYED")
      goto(Destroyed) using newStateData

    case Event(Fail, f: Failure) =>
      goto(Failed)
  }

  when(Destroyed){
    case Event(_,_) =>

      stay()
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

        // update the central load balancer
        log.debug("Requesting update of load balancer")
        lbManager ! AddFrontendBackend(vrn = vrn, port = port, mode = mode)

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

          lbManager ! PushServicesToLocalProxies

          jobExecutor ! UpdateJob(Jobs.status("finished"))

      }

    /**
     *
     * Transitions  undeployment
     *
     */

    case Idle -> WaitingUnExpose =>
      nextStateData match {
        case ServiceState(state) =>
          sendStateUpdate(state)

          //todo: Split removal from lb and zookeeper in separate states

          log.info("Removing service from Zookeeper")

          lbManager ! RemoveServiceFromLocalProxies(vrn)

          log.debug("Removing service from load balancer")

          // remove the service from the load balancer
          lbManager ! RemoveFrontendBackend(vrn)

      }

    case WaitingUnExpose -> WaitingDestroy =>
      nextStateData match {
        case ServiceState(state) =>
          sendStateUpdate(state)

          log.debug("Requesting undeployment of underpinning containers")


          // then start destroying all containers.
          // This is done by getting all the container and submitting an undeploy job for each.
          // In the same action, we supply the amount of containers to the next state, so we can
          // wait for all of them to be destroyed.

          // Todo: use jobs list to watch for successful completion of jobs before proceeding
          // Todo: split destruction of containers and service
          // Todo: destroying multiple containers in one go does not work.
          DB.withTransaction( implicit session => {

            val containers = DockerContainers.findByServiceId(service.id.get)

            val jobsList : List[Long] = List(0)

            containers.map( cnt => {

              val builder = new UnDeploymentJobBuilder
              builder.setContainer(cnt)
              val jobId = builder.build
              jobsList.::(List(jobId))
              log.debug("List is " + jobsList.toString())
            })

            // Destroy the service in the database
            DB.withSession( implicit session => {

              Services.delete(service.id.get)

            })

            self ! DestroyFinish

          })

      }


    case WaitingDestroy -> Destroyed =>
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
