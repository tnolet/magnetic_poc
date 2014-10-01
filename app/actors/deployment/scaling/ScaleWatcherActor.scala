package actors.deployment.scaling

import akka.actor.{ActorRef, Cancellable, ActorLogging, Actor}
import lib.marathon.Marathon
import play.api.libs.json.{JsObject, JsNull, JsValue}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
 * ScaleWatcherActor is started up to watch for changes in the scaling of containers.
 * It reports back the state of a scaling process. Its initial goal is to notice when the the amount
 * of running instances changes and all underlying Mesos tasks are running
 */

// messages
sealed trait ScaleWatcherMessage
case class Watch(vrn: String) extends ScaleWatcherMessage
case object CheckTasks extends ScaleWatcherMessage

class ScaleWatcherActor extends Actor with ActorLogging {

  private var scheduler: Cancellable = _
  private var vrn: String = _
  private var originalSender: ActorRef = _

  override def postStop(): Unit = {
    scheduler.cancel()
  }

  def receive = {

    case Watch(_vrn) =>

      vrn = _vrn
      originalSender = sender()
      scheduler = context.system.scheduler.scheduleOnce(3 seconds, self, CheckTasks)

      log.info(s"Started watcher for scaling tasks on $vrn")

      // Check tasks does a very simple check. It compares 'instances' with 'tasksStaged' and 'tasksRunning'
      // in the Marathon API. Rules are
      // 1. 'instances' should equal 'tasksRunning'
      // 2. 'tasksStaged' should be 0
    case CheckTasks =>

      log.info(s"Checking scaling for appId: $vrn")

      val futureApp = Marathon.app(vrn)
      futureApp.map( app => {

        val appObj = (app \ "app").as[JsObject]

        val instances = ( appObj \ "instances").as[Int]
        val tasksStaged = ( appObj \ "tasksStaged").as[Int]
        val tasksRunning = ( appObj \ "tasksRunning").as[Int]

        // all is good
        if (instances == tasksRunning && tasksStaged == 0) {
          originalSender ! ScaleOk
          scheduler.cancel()
        } else { reschedule() }
      }
    )
  }

  def reschedule() : Unit = {
    // Schedule another check
    scheduler = context.system.scheduler.scheduleOnce(3 seconds, self, CheckTasks)
  }
}