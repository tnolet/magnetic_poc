package controllers

import actors.loadbalancer.{LbFail, LbSuccess, UpdateBackendServerWeight}
import lib.job.{Horizontal, ScaleJobBuilder}
import lib.util.date.TimeStamp
import models.docker._
import models.{Job, Jobs}
import models.service.{ServiceResult, ServiceCreate, Services, Service}
import play.api.Logger
import play.api.db.slick._
import play.api.libs.concurrent.Akka
import play.api.libs.json.{JsError, Json}
import play.api.mvc._
import play.api.Play.current
import scala.concurrent.duration._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global

object ServiceController extends Controller {

  val lbManager = Akka.system.actorSelection("akka://application/user/lbManager/lbManager")


  import models.service.ServiceJson.ServiceWrites
  import models.service.ServiceJson.ServiceReads


  def list = DBAction { implicit rs =>
    val services = Services.all
    Ok(Json.toJson(services))
  }

  def find_by_id(id: Long) = DBAction { implicit rs =>

    import models.service.ServiceJson.ServiceResultWrites

    val service = Services.findById(id)

    service match {
      case Some(srv: Service) =>

        val containers : List[DockerContainer] =  DockerContainers.findByServiceId(srv.id.get)

        val containersResult : List[DockerContainerResult] = containers.map (cnt => {
          val instances =  ContainerInstances.findByContainerId(cnt.id.get)
          DockerContainerResult.createResult(cnt, instances)
        })
        val servRes = ServiceResult(srv.id,srv.port,srv.state, srv.vrn,srv.serviceTypeId,containersResult)

        Ok(Json.toJson(servRes))
      case None => NotFound("No service found")
    }
  }

  def find_containers_by_id(id: Long) = DBAction { implicit rs =>

    import models.docker.DockerContainerJson.containerResultWrites

    val _service = Services.findById(id)
    _service match {
      case Some(service : Service) =>
        val containers = DockerContainers.findByServiceId(service.id.get)

        val containersResult : List[DockerContainerResult] = containers.map (cnt => {
          val instances =  ContainerInstances.findByContainerId(cnt.id.get)
          DockerContainerResult.createResult(cnt, instances)

        })

        Ok(Json.toJson(containersResult))

      case None => NotFound(s"No service found with id $id")
    }
  }

  /**
   * Takes in a POSTED json message and sets up a job to create the requested service
   * @return the id of the created job
   */
    def create = DBAction(parse.json) { implicit rs =>

      import models.service.ServiceJson.serviceReadsforCreate

      rs.request.body.validate[ServiceCreate].fold(
        valid = { newService =>

              val jobId = createServiceDeployJob(newService)
              Created(s"jobId: $jobId ")
        },
        invalid = {
          errors => BadRequest(Json.toJson(JsError.toFlatJson(errors)))
        }
      )
    }

  /**
   * Updates the load balancer weight of a container and its instances/servers in the context of a service
   * @param id  the id of the service the container belongs to
   * @param containerVrn the unique VRN of the container
   * @param weight the weight to set.
   */
  def set_weight(id: Long, containerVrn: String, weight: Int) = Action.async {

    val (serviceVrn,instances) =  DB.withSession( implicit s =>
      {
        val _serviceVrn = Services.findById(id).map {
          case (srv: Service) =>
            srv.vrn
        }

        val _instances = DockerContainers.findInstancesByVrn(containerVrn).get

        (_serviceVrn,_instances)
      }
    )

    implicit val timeout = akka.util.Timeout(5 seconds)

    (lbManager ? UpdateBackendServerWeight(weight, instances, serviceVrn.get)).map {
      case LbSuccess =>
        DB.withSession( implicit s => {
          DockerContainers.updateWeightByVrn(vrn = containerVrn, weight = weight)
          Ok
        })
      case LbFail => InternalServerError
    }

  }

  /**
   * Updates the amount of instances of an existing container. There is no guarantee the requested amount
   * will be provisioned. This all depends on the available resources
   * @param id The id of the service
   * @param containerVrn  The vrn of the container
   * @param amount the amount of instances wanted
   */
  def set_instance_amount(id: Long, containerVrn: String, amount: Long) = DBAction { implicit rs =>

    val service = Services.findById(id)

    service match {
      case Some(srv: Service) =>

        val _container = DockerContainers.findByVrn(containerVrn)

        _container match {
          case Some(cnt: DockerContainer) =>

            //set the desired instance amount

            DockerContainers.setInstanceAmount(cnt.id.get, amount )

            // create a scaleJob
            val builder = new ScaleJobBuilder
            val scaleType = Horizontal(srv.vrn, cnt, amount)

            builder.setScaleType(scaleType)
            val jobId = builder.build

            Created(s"jobId: $jobId ")

          case None => NotFound("No such container found")
        }
      case None => NotFound("No such service found")
    }
  }

  // todo: cascade delete depending containers
  def delete(id: Long) = DBAction { implicit rs =>
    Services.delete(id)
    NoContent
  }

  /**
   *
   * PRIVATE
   *
   */


  /**
   * createServiceDeployJob creates a deployment job based on an service and returns the id of the created job
   * @param newService is an object of the type [[ServiceCreate]]
   */
  private def createServiceDeployJob(newService: ServiceCreate) : Long = {

    import models.service.ServiceJson.ServiceWritesforCreate
    var newJobId: Long = 0

    play.api.db.slick.DB.withTransaction { implicit session =>

      val timestamp = TimeStamp.now

      newJobId = Jobs.insert(new Job(
        Option(0), // temporary id, will be discarded
        Jobs.status("new"), // status
        1, // priority
        Json.stringify(Json.toJson(newService)), // payload
        Jobs.queue("serviceDeployment"), // queue
        timestamp, // created timestamp
        timestamp) // updated timestamp
      )
    }
    newJobId
  }

}
