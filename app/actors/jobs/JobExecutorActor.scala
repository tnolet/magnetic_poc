package actors.jobs

import actors.deployment.scaling.SubmitInstanceScaling
import actors.deployment.{SubmitServiceUnDeployment, SubmitServiceDeployment, SubmitUnDeployment, SubmitDeployment}
import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive
import lib.job._
import lib.util.date.TimeStamp
import models.docker._
import models.service.{Services, Service, ServiceCreate}
import models._
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

        case "SERVICE_UNDEPLOYMENT" => executeServiceUnDeployment()

        case "SCALING" => executeScaling()

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

    // read the job
    deployable.read(job)

    // Create a unique VRN for this resource
    val vrn = lib.util.vamp.Naming.createVrn("container")

    // create a container record in the database based on the information in the deployment job
    DB.withTransaction { implicit session =>


            // check if the service we want to deploy the container to exists
            val _service = Services.findByVrn(deployable.service)
            _service match {

              case Some(service) =>

                // Create the container
                val cntId = DockerContainers.insert(
                  new DockerContainer(Option(0),
                    vrn,
                    "INITIAL",
                    deployable.image.repo,
                    deployable.image.version,
                    0,
                    1,
                    service.id.getOrElse(1),                            //serviceId
                    TimeStamp.now))

                // start the deployment
                deployer ! SubmitDeployment(vrn,
                  deployable.image,
                  deployable.service)

              case None =>
                log.error(s"No service found with vrn ${deployable.service}")
                self ! addJobEvent("FAILED","deployment")
                self ! UpdateJob(Jobs.status("failed"))}
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

    val deployable = new ServiceDeploymentJobReader
    deployable.read(job)

          // Create a unique VRN for this resource
          val vrn = lib.util.vamp.Naming.createVrn("service","development")

          DB.withTransaction { implicit session =>

            Services.insert(
              new Service(Option(0),
                deployable.service.port,
                "INITIAL",
                vrn,
                deployable.service.environmentId,
                deployable.service.serviceTypeId))
          }

          deployer ! SubmitServiceDeployment(vrn,deployable.service)
    }


  def executeServiceUnDeployment(): Unit = {

    val deployer = context.actorSelection("/user/deployer")
    val undeployable = new ServiceUndeploymentJobReader

    undeployable.read(job)

    deployer ! SubmitServiceUnDeployment(undeployable.service)

  }

  /**
   * Parses the payload of a job in the Scaling queue and executes the requested scaling transaction
   */
  def executeScaling() : Unit = {

    // The deployer parent actor also manages the scaling actors
    val deployer = context.actorSelection("/user/deployer")

    val scalable = new ScaleJobReader
    scalable.read(job)

    scalable.scaleType match {

      case (st : Horizontal) =>

        deployer ! SubmitInstanceScaling(st.serviceVrn, st.container, st.instanceAmount)

      case _ =>

    }



  }
}
