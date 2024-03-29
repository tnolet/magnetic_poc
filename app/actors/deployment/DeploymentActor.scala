package actors.deployment

import akka.actor._
import lib.marathon.Marathon
import actors.loadbalancer.{RemoveBackendServer, AddBackendServer, LbFail, LbSuccess}
import lib.util.date.TimeStamp
import models.docker._
import actors.jobs.{addJobEvent, UpdateJob}
import models.loadbalancer.BackendServerCreate
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
case class SubmitDeployment(vrn: String, image: DockerImage, service : String, amount: Int) extends DeployEvent
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
trait Data
case object Uninitialized extends Data
case class ContainerState(state: String) extends Data
case class Failure(reason: String)

class DeploymentActor extends Actor with LoggingFSM[DeployState, Data]{

  private var originalSender: ActorRef = _
  private var watcher: ActorRef = _
  private var eventType: String = _

  private var vrn: String = _
  private var repo: String = _
  private var version: String = _
  private var image: DockerImage = _
  private var service: String = _
  private var amount: Int = _

  val lbManager = context.actorSelection("akka://application/user/lbManager")

  startWith(Idle, Uninitialized)

  when(Idle) {

      // Initial message for starting a deployment
      case Event(depl : SubmitDeployment, Uninitialized) =>

        // Set all variables for the container we are going to deploy

        originalSender = sender()
        vrn         = depl.vrn
        repo        = depl.image.repo
        version     = depl.image.version
        image       = depl.image
        service     = depl.service
        amount      = depl.amount
        eventType   = "deployment"

        log.info(s"Staging deployment of image $repo:$version with unique ID $vrn")

        val newState = new ContainerState("STAGING")

        goto(Staging) using newState


      // Initial message for starting an undeployment
      case Event( undepl: SubmitUnDeployment, Uninitialized) =>

        originalSender = sender()
        vrn         = undepl.vrn
        eventType   = "undeployment"


        log.info(s"Starting the undeployment of $vrn")

        val newState = new ContainerState("WAITING_FOR_UNEXPOSE")

        goto(WaitingUnExpose) using newState
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

  // WaitingExposure is the state for waiting if the load balancer connects correctly
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
      val newStateData = c.copy("LIVE_WITH_FAILED_UNEXPOSURE")
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

  /**
   *
   * Transitions
   *
   */


  onTransition {


      // on the transition from Idle to Staging we have to start watching Marathon for state change to running
    case Idle -> Staging =>
      nextStateData match {
        case ContainerState(state) =>

          sendStateUpdate(state)

          Marathon.submitContainer(vrn, image, amount).map(
            i => {
              if (i < 399) {

                // Marathon reports everything is OK: start to stage the container
                log.info(s"Staging $vrn to Marathon successful: response code: $i")

                //We should now start an actor that watches the staging fase on Marathon and reports success or failure
                //We give the watcher the VRN of the container and the amount of instances it should expect to watch
                watcher = context.actorOf(Props[TaskWatcherActor], "watcher")
                watcher ! Watch(vrn, amount)

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

          sendStateUpdate(state)

          //Stop the TaskWatcher
          watcher ! PoisonPill

          //Expose it to the load balancer
          self ! RunExpose
      }

    case Running -> WaitingExposure =>
      nextStateData match {
        case ContainerState(state) =>

          sendStateUpdate(state)

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

              var serverList = List[BackendServerCreate]()

              // create a list of container instances to deploy as backend servers to the load balancer
              tasksList.foreach { task =>

                // Get the relevant data
                val host = (task \ "host").as[String]
                val port = (task \ "ports")(0).as[Int]
                val mesosId = (task \ "id").as[String]

                // Create a unique VRN for this instance
                val vrnInstance = lib.util.vamp.Naming.createVrn("instance")

                createContainerInstance(vrnInstance,host, port.toString, mesosId)

                serverList = serverList.::(BackendServerCreate(host,port,vrnInstance,service))

              }

              // add the container as a server to the load balancer
              lbManager ! AddBackendServer(serverList)

            }
          }
        )
      }

    case WaitingExposure -> Live =>
      nextStateData match {
        case ContainerState(state) =>

          sendStateUpdate(state)

          originalSender ! UpdateJob(Jobs.status("finished"))
      }

    case WaitingExposure -> Failed =>
      nextStateData match {
        case ContainerState(state) =>

          sendStateUpdate(state)

          originalSender ! UpdateJob(Jobs.status("failed"))
      }

    case _ -> Failed =>
      nextStateData match {
        case ContainerState(state) =>

          sendStateUpdate(state)

          originalSender ! UpdateJob(Jobs.status("failed"))
      }

/**
 *
 * Transitions  undeployment
 *
 */

      // We unexpose all server instances in the loadbalancer belonging to container/backend.
      // We can't just delete a full backend, because there might be servers belonging to different containers.
    case Idle -> WaitingUnExpose =>
      nextStateData match {
        case ContainerState(state) =>
          sendStateUpdate(state)

          getContainerInstances(vrn).map {

            case list : List[String] =>

              log.info(s"Got list of instance to delete: $list ")
              lbManager ! RemoveBackendServer(list)

          }


      }

    // Start killing the container on Marathon
    case WaitingUnExpose -> WaitingDestroy =>
      log.debug("Asking marathon for delete")

      nextStateData match {
        case ContainerState(state) =>

          sendStateUpdate(state)


            /**
             *  Destroy the container. Some rules apply.
             *  1. If Marathon, for some reason, cannot find the container and reports a 404, we proceed with deletion
             */

          Marathon.destroyContainer(vrn).map(
            i => {
              if (i < 399) {
                // Marathon reports everything is OK: the container will be destroyed
                log.info(s"Destroying $vrn on Marathon successful: response code: $i")

                self ! DestroyFinish
              } else if ( i == 404) {

                // container is somehow not existent. Proceed with deletion
                log.warning(s"Did not find container $vrn on Marathon while destroying. Proceeding with destroy")

                self ! DestroyFinish
              }
              else {
                log.error(s"Destroying $vrn on Marathon has errors: response code: $i")

                originalSender ! UpdateJob(Jobs.status("failed"))

                self ! Fail
              }
            }
          )
      }

    case WaitingDestroy -> Destroyed =>
      nextStateData match {
        case ContainerState(state) =>
          sendStateUpdate(state)

          originalSender ! UpdateJob(Jobs.status("finished"))

      }
  }

  initialize()

  /**
   *
   * Updates the state of all who want to know. In this case specifically the container and the job
   * by the means of job events.
   *
   * @param state the current state of the machine. The eventType is a local var
   */
  
  def sendStateUpdate(state: String) : Unit = {

    // Update the job events

    originalSender ! addJobEvent(state, eventType)

    // Update the container
    DB.withSession { implicit session: Session =>
      DockerContainers.updateStateByVrn(vrn,state)
    }

  }

  def createContainerInstance(vrnInstance: String, host: String, port: String, mesosId: String) : Unit = {
    DB.withSession { implicit session: Session =>

      val instance = ContainerInstanceCreate(
        vrnInstance,
        host,
        port.toString,
        0,
        mesosId,
        TimeStamp.now)

      DockerContainers.createInstanceByVrn(vrn, instance)

    }
  }

  def getContainerInstances(vrn: String) : Option[List[String]] = {
    DB.withSession { implicit session: Session =>
      DockerContainers.findInstancesByVrn(vrn).map {

        case list: List[ContainerInstance] =>
          for (inst <- list) yield inst.vrn
      }
    }
  }

}
