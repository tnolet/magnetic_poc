package actors.jobs

import actors.deployment.{SubmitServiceDeployment, SubmitUnDeployment, SubmitDeployment}
import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive
import lib.job.{UnDeploymentJobReader, DeploymentJobReader}
import lib.util.date.TimeStamp
import models.docker._
import models.service.{Services, Service, ServiceCreate}
import models.{JobEvent, Jobs, Job}
import play.api.db.slick._
import play.api.libs.json._
import play.api.db.slick.DB


/**
 * A JobExecutorActor is started by the [[JobManagerActor]] to take care of running the job once it has been identified.
 * This is necessary so the Manager is free to process new jobs.
 */
case class UpdateJob(status: String) extends JobsMessage
case class addJobEvent(status: String, eventType: String) extends JobsMessage

class JobExecutorActor(job: Job) extends Actor with ActorLogging {

  import play.api.Play.current

  private val id = job.id.get
  private val queue = job.queue
  private val payload = job.payload

  def receive = LoggingReceive {

    case StartJob =>

      log.debug(s"JobExecutor started for job $id in queue $queue ")
      queue match {

        case "DEPLOYMENT" => executeDeployment()

        case "UNDEPLOYMENT" => executeUnDeployment()

        case "SERVICE_DEPLOYMENT" => executeServiceDeployment()

        case _ => log.error(s"Found job with unknown queue $queue")
      }

    /**
     * Updates the general Job status, e.g. whether it is running, finished or failed
     */
    case addJobEvent(status,eventType) =>

      log.info(s"Adding job event $eventType with status $status")

      // create the JobEvent
      val event = new JobEvent(Some(0),status,eventType,id,TimeStamp.now)

      // insert it
      DB.withSession { implicit session: Session =>
        Jobs.insertJobEvent(id,event)
      }

    /**
     * Adds events to jobs
     */
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
    val deployable = new DeploymentJobReader

    deployable.read(job)

    // create a container record in the database based on the information in the deployment job
    DB.withTransaction { implicit session =>

      // Create a unique VRN for this resource
      val vrn = lib.util.vamp.Naming.createVrn("container", deployable.environment)

      DockerContainers.insert(
        new DockerContainer(Option(0),
          vrn,
          "INITIAL",
          deployable.image.repo,
          deployable.image.version,
          "",
          1,
          TimeStamp.now))

      // start the deployment
      deployer ! SubmitDeployment(vrn,
        deployable.image,
        deployable.environment,
        deployable.service)
    }
  }


  def executeUnDeployment(): Unit = {

    val deployer = context.actorSelection("/user/deployer")
    val undeployable = new UnDeploymentJobReader

    undeployable.read(job)

    deployer ! SubmitUnDeployment(undeployable.vrn)

  }

  /**
   * Parses the payload of a job in the Service deployment queue, creates a service for it and submits the job to
   * the service deployer.
   */
  def executeServiceDeployment() : Unit = {

    val deployer = context.actorSelection("/user/deployer")

    import models.service.ServiceJson.serviceReadsforCreate

    Json.parse(payload)
      .validate[ServiceCreate]
      .fold(
        valid = { service => {

          // Create a unique VRN for this resource
          val vrn = lib.util.vamp.Naming.createVrn("service","dev")

          DB.withTransaction { implicit session =>

            Services.insert(
              new Service(Option(0),
                service.port,
                "INITIAL",
                vrn,
                service.environmentId,
                service.serviceTypeId))
          }

          deployer ! SubmitServiceDeployment(vrn,service)
        }
      },
        invalid = { errors => log.error(s"Invalid payload in job with id: ${job.id}. Errors: " + errors) }
      )
  }
}
