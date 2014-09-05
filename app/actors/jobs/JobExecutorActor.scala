package actors.jobs

import actors.deployment.Submit
import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive
import models.docker._
import models.{Jobs, Job}
import play.api.db.slick._
import play.api.libs.json.Json


/**
 * A JobExecutorActor is started by the [[JobManagerActor]] to take care of running the job once it has been identified.
 * This is necessary so the Manager is free to process new jobs.
 *
 */
class JobExecutorActor(job: Job) extends Actor with ActorLogging {

  import play.api.Play.current

  // Json reading/writing
  implicit val imageReads = Json.reads[DockerImage]
  implicit val imageWrites = Json.writes[DockerImage]

  private val id = job.id.get
  private val queue = job.queue
  private val payload = job.payload

  def receive = LoggingReceive {

    case StartJob =>

      log.debug(s"JobExecutor started for job $id in queue $queue ")
      queue match {
        case "DEPLOYMENT" => executeDeployment()
        case "UNDEPLOYMENT" => log.info("goto undeployment")
        case _ => log.error(s"Found job with unknown queue $queue")
      }

    case UpdateJob(status) =>
      log.info(s"Updating job $id with status $status")
      DB.withSession { implicit session: Session =>
        Jobs.update_status(id,status)
      }
  }

  def executeDeployment(): Unit = {
    val deployer = context.actorSelection("/user/deployer")

    Json.parse(payload)
      .validate[DockerImage]
      .fold(
        valid = { image => deployer ! Submit(id, image)},
       invalid = { errors => log.error(s"Invalid payload in job with id: ${job.id}. Errors: " + errors) }
      )
    }
  }
