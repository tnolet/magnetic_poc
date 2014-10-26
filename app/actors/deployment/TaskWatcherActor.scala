package actors.deployment

import akka.actor.{ActorRef, Cancellable, ActorLogging, Actor}
import lib.marathon.Marathon
import play.api.libs.json.{JsNull, JsValue}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


/**
 * TaskWatcherActor is started up to watch for changes in a staging process, represented as Tasks in Mesos.
 * It reports back the state of a staging process. Its goal is to notice when "staging" becomes "running".
 * It can watch an arbitrary amount of tasks, in the case multiple tasks are started at once.
 * When all tasks have been started successfully, it will issue a [[RunStart]] message
 * to the original sender
 */

// messages
sealed trait TaskWatcherMessage
case class Watch(vrn: String, taskAmount: Int) extends TaskWatcherMessage
case object CheckTasks extends TaskWatcherMessage

class TaskWatcherActor extends Actor with ActorLogging {

  private var scheduler: Cancellable = _
  private var vrn: String = _
  private var originalSender: ActorRef = _
  private var taskAmount : Int = _
  private var tasksCompleted : Int = _


  override def postStop(): Unit = {
    scheduler.cancel()
  }

  def receive = {
    case w: Watch =>

      vrn             = w.vrn
      taskAmount      = w.taskAmount
      originalSender  = sender()
      scheduler       = context.system.scheduler.scheduleOnce(3 seconds, self, CheckTasks)

      log.info(s"Started watcher for tasks on $vrn")

    case CheckTasks =>

      log.info(s"Checking tasks for appId: $vrn")


      val futureTasks = Marathon.tasks(vrn)
      futureTasks.map( tasks => {
          val tasksList = (tasks \ "tasks").as[List[JsValue]]

          // the tasks list can be unpopulated due to queuing in Mesos/Marathon. Also, tasks are added to list in an
          // non-deterministic manner. For now, we wait until the tasks list has the amount of tasks expected
          if (tasksList.length == taskAmount) {

            // loop over the full list of task and increase the tasksCompleted counter until
            // it matches the taskAmount. If not successful, reschedule and try it again.
            tasksList.foreach { task =>

              val startTime = task \ "startedAt"

              // as long as the value for "startedAt" is null, the task has not started yet
              if (startTime != JsNull) {

                log.info("Tasks for appId %s started at %s".format(vrn, startTime.toString))
                tasksCompleted+= 1
              }

            }

            if (tasksCompleted == taskAmount) {

              log.info(s"All $taskAmount tasks have started successfully")
              originalSender ! RunStart
              scheduler.cancel()

            } else {

              //reset the counter and reschedule

              log.info(s"Mesos reports $tasksCompleted of $taskAmount have started. Waiting for other tasks to start...")

              tasksCompleted = 0
              reschedule()
            }

          } else {

            log.info(s"Mesos task list has ${tasksList.length} tasks. Waiting for all $taskAmount tasks to enter the list...")
            reschedule()

          }
        }
      )
  }

  def reschedule() : Unit = {
    // Schedule another check
    scheduler = context.system.scheduler.scheduleOnce(3 seconds, self, CheckTasks)
  }
}

