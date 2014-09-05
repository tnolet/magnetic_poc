package actors.deployment

import akka.actor.{ActorRef, Cancellable, ActorLogging, Actor}
import lib.marathon.Marathon
import play.api.libs.json.{JsNull, JsValue}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


/**
 * TaskWatcherActor is started up to watch for changes in a staging process, represented as Tasks in Mesos.
 * It reports back the state of a staging process. Its goal is to notice when "staging" becomes "running"
 */

sealed trait WatcherMessage
case class Watch(vrn: String) extends WatcherMessage
case object CheckTasks extends WatcherMessage

class TaskWatcherActor extends Actor with ActorLogging {

  private var scheduler: Cancellable = _
  private var vrn: String = _
  private var originalSender: ActorRef = _

  override def postStop(): Unit = {
    scheduler.cancel()
  }

  def receive = {
    case Watch(_vrn) =>

      vrn             = _vrn
      originalSender  = sender()
      scheduler       = context.system.scheduler.scheduleOnce(3 seconds, self, CheckTasks)

      log.info(s"Started watcher for tasks on $vrn")

    case CheckTasks =>

      log.info(s"Checking tasks for appId: $vrn")

      val futureTasks = Marathon.tasks(vrn)
      futureTasks.map( tasks => {
          val tasksList = (tasks \ "tasks").as[List[JsValue]]

          // the Tasks list can be unpopulated due to queuing in Mesos/Marathon
          if (tasksList.nonEmpty) {


            val startTime = tasksList(0) \ "startedAt"

            // as long as the value for "startedAt" is null, the task has not started yet
            if (startTime != JsNull) {

              log.info("Tasks for appId %s started at %s".format(vrn, startTime.toString))
              originalSender ! RunStart
              scheduler.cancel()

            } else { reschedule() }
          } else { reschedule() }
      }
      )
  }

  def reschedule() : Unit = {
    // Schedule another check
    scheduler = context.system.scheduler.scheduleOnce(3 seconds, self, CheckTasks)
  }
}

