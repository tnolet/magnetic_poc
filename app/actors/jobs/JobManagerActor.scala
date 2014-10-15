package actors.jobs

import akka.actor.{Props, ActorLogging, Actor}
import akka.event.LoggingReceive
import models.{Job, Jobs}
import models.docker.DockerImage
import play.api.db.slick._
import play.api.Play.current
import play.api.libs.json._


trait JobsMessage
case object CheckJobs extends JobsMessage
case object StartJob extends JobsMessage

/**
 * JobManagerActor checks the database for new Jobs. It picks up new jobs, assigns them to actors that perform the work
 * and updates the status of a job.
 */
class JobManagerActor extends Actor with ActorLogging {

  // Json reading/writing
  implicit val imageReads = Json.reads[DockerImage]
  implicit val imageWrites = Json.writes[DockerImage]

  def receive =  LoggingReceive {

    case CheckJobs =>
      //log.debug("Job Manager is checking for new jobs")
      DB.withTransaction { implicit session: Session =>
        Jobs
          .all(None)
          .filter(_.status == "NEW")
          .foreach(job => {
          startJobExecutor(job)
          }
        )
      }
  }

  def startJobExecutor(job: Job) = {

    val props = Props(new JobExecutorActor(job))
    val executor = context.actorOf(props, "jobExecutor_"+ job.id.get.toString)
    executor ! StartJob

    DB.withSession { implicit session: Session =>
      Jobs.update_status(job.id.get, Jobs.status("active"))
    }

  }

}
