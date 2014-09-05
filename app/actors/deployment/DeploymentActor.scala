package actors.deployment

import akka.actor._
import lib.marathon.Marathon
import actors.loadbalancer.{RemoveBackendServer, AddBackendServer, LbFail, LbSuccess}
import models.docker.{DockerContainers, DockerImage}
import actors.jobs.UpdateJob
import play.api.db.slick._
import play.api.libs.json.JsValue
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import models.Jobs
import play.api.Play.current

/**
 *
 * State defines all the possible stages in the the deployment pipeline
 *
 */


//events for deploying
trait DeployEvent
case class SubmitDeployment(vrn: String, image: DockerImage) extends DeployEvent
case object Stage extends DeployEvent
case object RunWait extends DeployEvent
case object RunStart extends DeployEvent
case object RunExpose extends DeployEvent
case object DestroyFinish extends DeployEvent
case class Fail(reason: String) extends DeployEvent

//events for undeploying
case class SubmitUnDeployment(vrn: String) extends DeployEvent
case object UnExpose extends DeployEvent


trait DeployState

//states for deploying
case object Idle extends DeployState
case object Submitted extends DeployState
case object Staging extends DeployState
case object Waiting extends DeployState
case object WaitingExposure extends DeployState
case object Running extends DeployState
case object Live extends DeployState
case object Failed extends DeployState

//states for undeploying
case object WaitingUnExpose extends DeployState
case object WaitingDestroy extends DeployState
case object Destroyed extends DeployState

//state data
sealed trait Data
case object Uninitialized extends Data
case class ContainerState(status: String) extends Data
case class Failure(reason: String)


class DeploymentActor extends Actor with LoggingFSM[DeployState, Data]{

  private var jobExecutor: ActorRef = _
  private var watcher: ActorRef = _
  private var vrn: String = _
  private var repo: String = _
  private var version: String = _
  private var image: DockerImage = _

  val lbManager = context.actorSelection("akka://application/user/lbManager")

  startWith(Idle, Uninitialized)

  when(Idle) {

      // Initial message for starting a deployment
      case Event(SubmitDeployment(_vrn, _image), Uninitialized) =>

        // Set all variables for he container we are going to deploy

        jobExecutor = sender()
        vrn         = _vrn
        repo        = _image.repo
        version     = _image.version
        image       = _image

        log.info(s"Staging deployment of image $repo:$version with unique ID $vrn")

        val newState = new ContainerState("SUBMITTED")

        goto(Submitted) using newState

      // Inital message for starting an undeployment
      case Event(SubmitUnDeployment(_vrn), Uninitialized) =>

        jobExecutor = sender()
        vrn         = _vrn

        log.info(s"Starting the undeployment of $vrn")

        val newState = new ContainerState("WAITING_FOR_UNEXPOSE")

        goto(WaitingUnExpose) using newState
  }

  when(Submitted) {

    case Event(Stage, c: ContainerState) =>

      val newStateData = c.copy("STAGING")
      goto(Staging) using newStateData

    case Event(Fail,f: Failure) =>
      goto(Failed)

  }
  
  when(Staging) {

    case Event(RunWait, c: ContainerState) =>
      val newStateData = c.copy("WAITING_FOR_RUNNING")
      goto(Waiting) using newStateData

    case Event(Fail,f: Failure) =>
      goto(Failed)

  }
  /**
   * When in the Waiting state, we are effectively waiting for an answer from a [[TaskWatcherActor]]. It should update
   * us whether a state has changed on Marathon and we can proceed. If we never get message, we time out and fail.
   */
  when(Waiting, stateTimeout = 3 minutes) {

    case Event(StateTimeout, c: ContainerState) =>
      val newStateData = c.copy("TIMED_OUT")
      goto(Failed) using newStateData

    case Event(RunStart, c: ContainerState) =>
      val newStateData = c.copy("RUNNING")
      goto(Running) using newStateData

    case Event(Fail,f: Failure) =>
      goto(Failed)
  }

  when(Running) {

    case Event(RunExpose, c: ContainerState) =>
      val newStateData = c.copy("EXPOSING")
      goto(WaitingExposure) using newStateData

    case Event(Fail,f: Failure) =>
      goto(Failed)

  }

  // WaitingExposure is the state for waiting if the loadbalancer connects correctly
  when(WaitingExposure) {

    case Event(LbSuccess, c: ContainerState) =>
      val newStateData = c.copy("LIVE")
      goto(Live) using newStateData

    case Event(LbFail, c: ContainerState) =>
      val newStateData = c.copy("LIVE_WITH_FAILED_EXPOSURE")
      goto(Failed) using newStateData

    case Event(Fail, f: Failure) =>
      goto(Failed)
  }

  when(Live){
    case Event(_,_) =>

      stay()
  }


  /**
   *
   * undeploy states
   *
   */

  when(WaitingUnExpose){

    case Event(LbSuccess, c: ContainerState) =>
      val newStateData = c.copy("UNEXPOSED")
      goto(WaitingDestroy) using newStateData

    case Event(LbFail, c: ContainerState) =>
      val newStateData = c.copy("LIVE_WITH_FAILED_EXPOSURE")
      goto(Failed) using newStateData

    case Event(Fail, f: Failure) =>
      goto(Failed)
  }

  when(WaitingDestroy){

    case Event(DestroyFinish,c: ContainerState) =>
      val newStateData = c.copy("DESTROYED")
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

  //transitions

  onTransition {

    case Idle -> Submitted =>
      nextStateData match {
        case ContainerState(state) =>

          updateContainerState(state)

          Marathon.submitContainer(vrn, image).map(
            i => {
              if (i < 399) {
                // Marathon reports everything is OK
                log.info(s"Submit $vrn to Marathon successful: response code: $i")

                //Proceed to Staging fase with Deploy command. Staging can take time
                self ! Stage
                
              }
              else {
                log.error(s"Submit $vrn to Marathon has errors: response code: $i")
                self ! Fail
              }
            }
          )
      }

      // on the transition from Submitted to Staging we have to start watching Marathon for state change to running
    case Submitted -> Staging =>
      nextStateData match {
        case ContainerState(state) =>

          updateContainerState(state)

          Marathon.stageContainer(vrn, image).map(
            i => {
              if (i < 399) {
                
                // Marathon reports everything is OK: start to stage the container
                log.info(s"Staging $vrn to Marathon successful: response code: $i")

                //We should now start an actor that watches the staging fase on Marathon and reports success or failure
                watcher = context.actorOf(Props[TaskWatcherActor], "watcher")
                watcher ! Watch(vrn)

                //Proceed to Waiting fase and wait for the staging to finish and the deployment start running. Staging can take time
                self ! RunWait

              }
              else {
                log.error(s"Staging $vrn to Marathon has errors: response code: $i")
                self ! Fail
              }
            }
          )
      }

    case Waiting -> Running =>
      nextStateData match {
        case ContainerState(state) =>

          updateContainerState(state)

          //Stop the TaskWatcher
          watcher ! PoisonPill

          //Expose it to the load balancer
          self ! RunExpose
      }

    case Running -> WaitingExposure =>
      nextStateData match {
        case ContainerState(state) =>

          updateContainerState(state)


          /**
          * Add the running instance to the load balancer.
          * We grab the published port and IP from Marathon and hand it to the load balancer actor to update the
          * configuration.
          */


          val futureTasks = Marathon.tasks(vrn)
          futureTasks.map( tasks => {
            val tasksList = (tasks \ "tasks").as[List[JsValue]]

            // the Tasks list can be unpopulated due to queuing in Mesos/Marathon
            if (tasksList.nonEmpty) {

              // Get the relevant data
              val host = (tasksList(0) \ "host").as[String]
              val port = (tasksList(0) \ "ports")(0).as[Int]

              lbManager ! AddBackendServer(host,port,vrn)

            }

          })


      }

    case WaitingExposure -> Live =>
      nextStateData match {
        case ContainerState(state) =>

          updateContainerState(state)

          jobExecutor ! UpdateJob(Jobs.status("finished"))
      }

    case WaitingExposure -> Failed =>
      nextStateData match {
        case ContainerState(state) =>

          updateContainerState(state)

          jobExecutor ! UpdateJob(Jobs.status("failed"))
      }

    case _ -> Failed =>
      nextStateData match {
        case ContainerState(state) =>

          updateContainerState(state)

          jobExecutor ! UpdateJob(Jobs.status("failed"))
      }


    /**
     *
     * Transitions  undeployment
     *
     */


    case Idle -> WaitingUnExpose =>
      nextStateData match {
        case ContainerState(state) =>
          updateContainerState(state)

          lbManager ! RemoveBackendServer(vrn)

      }

    // Start killing the container on Marathon
    case WaitingUnExpose -> WaitingDestroy =>
      log.debug("Asking marathon for delete")

      nextStateData match {
        case ContainerState(state) =>
          updateContainerState(state)


          Marathon.destroyContainer(vrn).map(
            i => {
              if (i < 399) {
                // Marathon reports everything is OK: the container will be destroyed
                log.info(s"Destroying $vrn on Marathon successful: response code: $i")

                jobExecutor ! UpdateJob(Jobs.status("finished"))

                self ! DestroyFinish

              }
              else {
                log.error(s"Destroying $vrn on Marathon has errors: response code: $i")

                jobExecutor ! UpdateJob(Jobs.status("failed"))

                self ! Fail

              }
            }
          )
      }

    case WaitingDestroy -> Destroyed =>
      nextStateData match {
        case ContainerState(state) =>
          updateContainerState(state)

          jobExecutor ! UpdateJob(Jobs.status("finished"))

      }
  }





  initialize()

  def updateContainerState(state: String) : Unit = {

    DB.withSession { implicit session: Session =>
      DockerContainers.updateStatusByVrn(vrn,state)
    }
  }
}
