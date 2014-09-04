package actors.jobs

import akka.actor.{ActorRef, ActorLogging, Actor}
import akka.event.LoggingReceive
import models.{DockerImage, Jobs}
import play.api.db.slick._
import play.api.Play.current
import actors.deployment.Submit
import play.api.libs.json._


sealed trait JobsMessage
case class CheckJobs(deployer: ActorRef) extends JobsMessage
case class UpdateJob(id: Long, status: String) extends JobsMessage

/**
 * JobMonitorActor checks the database for new Jobs
 */
class JobManagerActor extends Actor with ActorLogging {

  // Json reading/writing
  implicit val imageReads = Json.reads[DockerImage]
  implicit val imageWrites = Json.writes[DockerImage]

  def receive =  LoggingReceive {
    case CheckJobs(deployer) =>
    log.info("Checking for new jobs")
      DB.withSession { implicit session: Session =>
        Jobs
          .all
          .filter(_.status=="NEW")
          .foreach( job => {
          log.info(s"Found new job with id: ${job.id.getOrElse("unknown id")}")

          //send job payload to deployer and mark as submitted
          Json.parse(job.payload).validate[DockerImage].fold(
            valid = { image =>
              log.info("Job payload is image" + job.payload)
              deployer ! Submit(job.id.get, image)
              Jobs.update_status(job.id.get, "VALIDATED")
            },
            invalid = {
              errors => log.error(s"Invalid payload in job with id: ${job.id}. Errors: " + errors)
            }
          )
        }
        )
      }
    case UpdateJob(id, status) =>
      log.info(s"Updating job $id with status $status")
      DB.withSession { implicit session: Session =>
        Jobs.update_status(id,status)
      }
  }

}
