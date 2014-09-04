package actors.deployment

import akka.actor._
import lib.marathon.Marathon
import models.DockerImage
import actors.loadbalancer.AddBackendServer
import play.api.libs.concurrent.Akka
import actors.jobs.UpdateJob
import play.api.libs.json.JsValue
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 *
 * State defines all the possible stages in the the deployment pipeline
 *
 */


//events
sealed trait DeployEvent
case class Submit(jobId: Long, image: DockerImage) extends DeployEvent
case object Stage extends DeployEvent
case object RunWait extends DeployEvent
case object RunStart extends DeployEvent
case object RunLive extends DeployEvent
case class Fail(reason: String) extends DeployEvent

//states
sealed trait State
case object Idle extends State
case object Submitted extends State
case object Staging extends State
case object Waiting extends State
case object Running extends State
case object Live extends State
case object Failed extends State

//state data
sealed trait Data
case object Uninitialized extends Data
case class Deployable(jobId: Long, image: DockerImage, marathonState: String) extends Data
case class Failure(reason: String)


class DeploymentActor extends Actor with LoggingFSM[State, Data]{

  private var watcher: ActorRef = _

  val jobManager = context.actorSelection("akka://application/user/jobManager")
  val lbManager = context.actorSelection("akka://application/user/lbManager")

  startWith(Idle, Uninitialized)

  when(Idle) {

      case Event(Submit(jobId, image), Uninitialized) =>
        log.info(s"Staging deployment of image: ${image.name}")
        val newState = new Deployable(jobId, image, "SUBMITTED")
        goto(Submitted) using newState
  }

  when(Submitted) {

    case Event(Stage, d: Deployable) =>
      val newStateData = d.copy(d.jobId, d.image, "STAGING")
      goto(Staging) using newStateData

    case Event(Fail,f: Failure) =>
      goto(Failed)

  }
  
  when(Staging) {
    case Event(RunWait, d: Deployable) =>
      val newStateData = d.copy(d.jobId, d.image, "WAITING_FOR_RUNNING")
      goto(Waiting) using newStateData

    case Event(Fail,f: Failure) =>
      goto(Failed)

  }
  /**
   * When in the Waiting state, we are effectively waiting for an answer from a [[TaskWatcherActor]]. It should update
   * us whether a state has changed on Marathon and we can proceed. If we never get message, we time out and fail.
   */
  when(Waiting, stateTimeout = 3 minutes) {
    case Event(StateTimeout, d: Deployable) =>
      val newStateData = d.copy(d.jobId, d.image, "TIMED_OUT")
      goto(Failed) using newStateData

    case Event(RunStart, d: Deployable) =>
      val newStateData = d.copy(d.jobId, d.image, "RUNNING")
      goto(Running) using newStateData
  }

  when(Running) {

    case Event(RunLive, d: Deployable) =>
      val newStateData = d.copy(d.jobId, d.image, "LIVE")
      goto(Live) using newStateData
  }

  when(Live){
    case Event(_,_) =>

      stay()
  }

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
        case Deployable(jobId, image, marathonState) =>
          Marathon.submitContainer(image).map(
            i => {
              if (i < 399) {
                // Marathon reports everything is OK
                log.info(s"Submit ${image.name} to Marathon successful: response code: $i")

                //Report status to Job Manager
                jobManager ! UpdateJob(jobId,marathonState.toUpperCase)

                //Proceed to Staging fase with Deploy command. Staging can take time
                self ! Stage
                
              }
              else {
                log.error(s"Submit ${image.name} to Marathon has errors: response code: $i")
                self ! Fail
              }
            }
          )
      }

      // on the transition from Submitted to Staging we have to start watching Marathon for state change to running
    case Submitted -> Staging =>
      nextStateData match {
        case Deployable(jobId, image, marathonState) =>
          Marathon.stageContainer(image).map(
            i => {
              if (i < 399) {
                
                // Marathon reports everything is OK: start to stage the container
                log.info(s"Staging ${image.name} to Marathon successful: response code: $i")

                // Report status to Job Manager
                jobManager ! UpdateJob(jobId,marathonState.toUpperCase)

                //We should now start an actor that watches the staging fase on Marathon and reports success or failure
                watcher = context.actorOf(Props[TaskWatcherActor], "watcher")
                watcher ! Watch(image)

                //Proceed to Waiting fase and wait for the staging to finish and the deployment start running. Staging can take time
                self ! RunWait

              }
              else {
                log.error(s"Staging ${image.name} to Marathon has errors: response code: $i")
                self ! Fail
              }
            }
          )
      }

    case Waiting -> Running =>
      nextStateData match {
        case Deployable(jobId, image, marathonState) =>

          //Report status to Job Manager
          jobManager ! UpdateJob(jobId,marathonState.toUpperCase)

          //Stop the TaskWatcher
          watcher ! PoisonPill

          //Bring it Live
          self ! RunLive
      }

    case Running -> Live =>
      nextStateData match {
        case Deployable(jobId, image, marathonState) =>

          /**
          * Add the running instance to the load balancer.
          * We grab the published port and IP from Marathon and hand it to the load balancer actor to update the
          * configuration.
          */

          // for now, we get it from the task data
          val appId = Marathon.appId(image.name, image.version)

          val futureTasks = Marathon.tasks(appId)
          futureTasks.map( tasks => {
            val tasksList = (tasks \ "tasks").as[List[JsValue]]

            // the Tasks list can be unpopulated due to queuing in Mesos/Marathon
            if (tasksList.nonEmpty) {

              // Get the relevant data
              val host = (tasksList(0) \ "host").as[String]
              val port = (tasksList(0) \ "ports")(0).as[Int]

              lbManager ! AddBackendServer(host, port, appId)



            }

          })


      }


    case _ -> Failed =>
      stateData match {
        case Deployable(jobId, _, marathonState) =>
        jobManager ! UpdateJob(jobId, "FAILED")
      }
  }

  initialize()
}
