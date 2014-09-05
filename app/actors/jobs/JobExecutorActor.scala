package actors.jobs

import java.sql.Timestamp

import actors.deployment.{SubmitUnDeployment, SubmitDeployment}
import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive
import lib.util.date.TimeStamp
import models.docker._
import models.{Jobs, Job}
import play.api.db.slick._
import play.api.libs.json._
import play.api.db.slick.DB
import play.api.libs.functional.syntax._


/**
 * A JobExecutorActor is started by the [[JobManagerActor]] to take care of running the job once it has been identified.
 * This is necessary so the Manager is free to process new jobs.
 */


case class UpdateJob(status: String) extends JobsMessage

class JobExecutorActor(job: Job) extends Actor with ActorLogging {



  import play.api.Play.current

  // Json reading/writing
  implicit val imageReads = Json.reads[DockerImage]
  implicit val imageWrites = Json.writes[DockerImage]


  // Json reading/writing
  implicit val containerWrites = Json.writes[DockerContainer]

  implicit val containerReads = (
    (__ \ 'id).read[Option[Long]] and
      (__ \ 'vrn).read[String] and
      (__ \ 'status).read[String] and
      (__ \ 'imageRepo).read[String] and
      (__ \ 'imageVersion).read[String] and
      (__ \ 'ports).read[String] and
      (__ \ 'created_at).read[Long].map{ long => new Timestamp(long) }
    )(DockerContainer)

  private val id = job.id.get
  private val queue = job.queue
  private val payload = job.payload

  def receive = LoggingReceive {

    case StartJob =>

      log.debug(s"JobExecutor started for job $id in queue $queue ")
      queue match {

        case "DEPLOYMENT" => executeDeployment()

        case "UNDEPLOYMENT" => executeUnDeployment()

        case _ => log.error(s"Found job with unknown queue $queue")
      }

    case UpdateJob(status) =>
      log.info(s"Updating job $id with status $status")
      DB.withSession { implicit session: Session =>
        Jobs.update_status(id,status)
      }
  }

  /**
   * Parses the payload of a job in the Deployment queue, creates a container for it and submits the job to the deployer.
   */
  def executeDeployment(): Unit = {
    val deployer = context.actorSelection("/user/deployer")

    Json.parse(payload)
      .validate[DockerImage]
      .fold(
        valid = { image => {

          // Create a unique VRN for this resource
          val vrn = lib.util.vamp.Naming.createVrn("container","dev")

          DB.withTransaction { implicit session =>

            DockerContainers.insert(
              new DockerContainer(Option(0),
                vrn,
                "INITIAL",
                image.repo,
                image.version,
                "",
                TimeStamp.now))
          }
          deployer ! SubmitDeployment(vrn, image)
        }
        },
       invalid = { errors => log.error(s"Invalid payload in job with id: ${job.id}. Errors: " + errors) }
      )
    }

  def executeUnDeployment(): Unit = {

    val deployer = context.actorSelection("/user/deployer")

    Json.parse(payload)
      .validate[DockerContainer]
      .fold(
        valid = { container => {
          deployer ! SubmitUnDeployment(container.vrn)
        }
      },
        invalid = { errors => log.error(s"Invalid payload in job with id: ${job.id}. Errors: " + errors) }
    )
  }

  }
