package actors.deployment.scaling

import actors.jobs.{UpdateJob, addJobEvent}
import actors.loadbalancer.{RemoveBackendServer, AddBackendServer}
import akka.actor.{Props, ActorRef, LoggingFSM, Actor}
import lib.marathon.Marathon
import lib.util.date.TimeStamp
import models.Jobs
import models.docker.{DockerContainer, ContainerInstanceCreate, ContainerInstance, DockerContainers}
import models.loadbalancer.BackendServerCreate
import play.api.db.slick._
import play.api.libs.json.JsObject
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play.current


/**
 * The ScalingActor performs the scaling of existing containers
 */

//events for scaling
trait ScaleEvent
case class SubmitInstanceScaling(serviceVrn: String, container: DockerContainer, instanceAmount: Long) extends  ScaleEvent
case object ScaleWait extends ScaleEvent
case object ScaleOk extends ScaleEvent
case object ScaleCompleted extends ScaleEvent
case class Fail(reason: String) extends ScaleEvent



//states for scaling
trait ScaleState

case object Idle extends ScaleState
case object Submitted extends ScaleState
case object Waiting extends ScaleState
case object Running extends ScaleState
case object Live extends ScaleState
case object Failed extends ScaleState

trait Data
case object Uninitialized extends Data
case class  ContainerScaleState(state: String) extends Data

class ScalingActor extends Actor with LoggingFSM[ScaleState, Data] {

  private var jobExecutor: ActorRef = _
  private var watcher: ActorRef = _
  private var eventType: String = _

  private var vrnService: String = _
  private var vrnContainer: String = _
  private var masterWeight: Int = _
  private var instanceAmount: Long = _

  val lbManager = context.actorSelection("akka://application/user/lbManager")


  startWith(Idle, Uninitialized)

  when(Idle) {

    // scaling instances, i.e. horizontal
    case Event((evn: SubmitInstanceScaling), Uninitialized) =>

      // Set all variables for the container we are going to scale

      jobExecutor = sender()
      vrnService = evn.serviceVrn
      vrnContainer = evn.container.vrn
      masterWeight = evn.container.masterWeight
      instanceAmount = evn.instanceAmount
      eventType = "scaling"


      log.info(s"Staging scaling of container $vrnContainer to $instanceAmount instances")

      val newState = new ContainerScaleState("SCALING_SUBMITTED")

      goto(Submitted) using newState

  }

  when(Submitted) {

    case Event(ScaleWait, c: ContainerScaleState) =>
      val newState = new ContainerScaleState("SCALING_WAITING")

      goto(Waiting) using newState

    case Event(Fail, c: ContainerScaleState) =>
      goto(Failed)

  }

  when(Waiting, stateTimeout = 3 minutes) {

    case Event(ScaleOk, c: ContainerScaleState) =>

      val newState = new ContainerScaleState("SCALING_COMPLETED")
      goto(Running) using newState

    case Event(Fail, c: ContainerScaleState) =>
      goto(Failed)

  }

  when(Running) {

    case Event(ScaleCompleted, c: ContainerScaleState) =>
      val newState = new ContainerScaleState("LIVE")

      goto(Live) using newState

    case Event(Fail, c: ContainerScaleState) =>
      goto(Failed)

  }

  when(Live) {

    case Event(_,_) =>
      stay()

    case Event(Fail, c: ContainerScaleState) =>
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

    case Idle -> Submitted =>
      nextStateData match {
        case ContainerScaleState(state) =>

          sendStateUpdate(state)

          Marathon.scaleContainer(vrnContainer,instanceAmount).map(
            i => {
              if (i < 399) {
                // Marathon reports everything is OK
                log.info(s"Submit scale event for $vrnContainer to Marathon successful: response code: $i")

                //start a watcher to check on the status of the scale event
                //We should now start an actor that watches the staging fase on Marathon and reports success or failure
                watcher = context.actorOf(Props[ScaleWatcherActor], "watcher")
                watcher ! Watch(vrnContainer)

                //Proceed to waiting fase. Scaling can take some time
                self ! ScaleWait

              }
              else {
                log.error(s"Submit scale event for $vrnContainer to Marathon has errors: response code: $i")
                self ! Fail
              }
            }
          )
      }

    case Submitted -> Waiting =>
      nextStateData match {
        case ContainerScaleState(state) =>

          sendStateUpdate(state)

      }

    case Waiting -> Running =>
      nextStateData match {
        case ContainerScaleState(state) =>

          sendStateUpdate(state)

        /**
         *  We need to align the Mesos Tasks with the Container instances as a result of scaling.
         *  1. New Tasks should be translated to new instances (enter...)
         *  2. Removed Tasks should result in deleted instances (exit...)
         *  3. Tasks with no change should have no impact (stay...)
         */

          // get all tasks in a list
           Marathon.tasks(vrnContainer).map(
            tasks => {

              val mesosIds = new scala.collection.mutable.MutableList[String]
              val tasksList =  (tasks \ "tasks").as[Seq[JsObject]]

              // get all current container instances in a list
              // and match them to the tasks
              DB.withSession { implicit session: Session =>
                DockerContainers.findInstancesByVrn(vrnContainer).map {
                  case (instances : List[ContainerInstance]) =>

                    tasksList.map( task => {

                      // get the host, port and id for a task
                      val host = (task \ "host").as[String]
                      val port = (task \ "ports")(0).as[Int]
                      val id = (task \ "id").as[String]

                      log.debug(s"Resolving Task/Instance mapping for $host $port $id")

                      //see if the tasks exists as a container
                      if ( instances.exists( ins => ins.mesosId == id )) {

                        // Stay: do nothing for now

                      } else {

                        // Enter: it doesn't exist as an instance, let's create it:

                        // Create a unique VRN for this instance
                        val vrnInstance = lib.util.vamp.Naming.createVrn("instance")

                        // insert into the database
                        log.debug(s"Creating server $vrnInstance because of scaling action")

                        createContainerInstance(vrnInstance,host,port.toString,id)

                        // add the container as a server to the load balancer
                        lbManager ! AddBackendServer(List(BackendServerCreate(host,port,vrnInstance,vrnService, weight = Some(masterWeight))))
                      }

                      // collect the mesosIds
                      mesosIds += id
                    })

                  // after all is done, proceed to check if any instances have been deleted
                  instances.map( ins => {

                    // if we have a match, all good
                    if ( mesosIds.contains(ins.mesosId))
                    {
                      //do nothing
                     }
                      else {

                      // there is an instance with a mesosId that's not matched with running mesos Task
                      // We should remove it
                      // add the container as a server to the load balancer

                      log.debug(s"Removing server ${ins.vrn} because of scaling action")
                      lbManager ! RemoveBackendServer(List(ins.vrn))

                      removeContainerInstance(ins.vrn)

                    }

                  })
                }

              }

            }

           )


          self ! ScaleCompleted

      }

    case Running -> Live =>
      nextStateData match {
        case ContainerScaleState(state) =>

          sendStateUpdate(state)

          jobExecutor ! UpdateJob(Jobs.status("finished"))

      }



    case _ -> Failed =>
      nextStateData match {
        case ContainerScaleState(state) =>

          sendStateUpdate(state)

          jobExecutor ! UpdateJob(Jobs.status("failed"))
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

    jobExecutor ! addJobEvent(state, eventType)

    // Update the container
    DB.withSession { implicit session: Session =>
      DockerContainers.updateStateByVrn(vrnContainer,state)
    }

  }

    def updateContainerInstance(host: String, port: String, mesosId: String) : Unit = {
    DB.withSession {implicit session: Session =>
      DockerContainers.updateInstanceByVrn(vrn = vrnContainer, host = host, port = port, mesosId = mesosId)
    }
  }

    def createContainerInstance(vrnInstance: String, host: String, port: String, mesosId: String) : Unit = {
      DB.withSession { implicit session: Session =>

        val instance = ContainerInstanceCreate(
          vrnInstance,
          host,
          port.toString,
          masterWeight,
          mesosId,
          TimeStamp.now)

        DockerContainers.createInstanceByVrn(vrnContainer, instance)

      }
    }

      def removeContainerInstance(vrnInstance: String): Unit = {
        DB.withSession { implicit session: Session =>
          DockerContainers.deleteInstanceByVrn(vrnContainer, vrnInstance)
        }
      }

}