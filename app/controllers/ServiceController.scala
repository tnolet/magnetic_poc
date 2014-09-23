package controllers

import actors.loadbalancer.{LbFail, LbSuccess, UpdateBackendServerWeight}
import lib.util.date.TimeStamp
import models.docker._
import models.{Job, Jobs}
import models.service.{ServiceResult, ServiceCreate, Services, Service}
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
          val _instance =  ContainerInstances.findByContainerId(cnt.id.get)
          _instance match {
            case Some(instance : ContainerInstance) =>
              DockerContainerResult(cnt.id, cnt.vrn, cnt.status, cnt.imageRepo, cnt.imageVersion, cnt.ports, cnt.serviceId, instance, cnt.created_at)
          }
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
          val _instance =  ContainerInstances.findByContainerId(cnt.id.get)
          _instance match {
            case Some(instance : ContainerInstance) =>
              DockerContainerResult(cnt.id, cnt.vrn, cnt.status, cnt.imageRepo, cnt.imageVersion, cnt.ports, cnt.serviceId, instance, cnt.created_at)
          }
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
   * Updates the load balancer weight of a container in the context of a service
   * @param id  the id of the service the container belongs to
   * @param containerVrn the unique VRN of the container
   * @param weight the weight to set.
   */
  def set_weight(id: Long, containerVrn: String, weight: Int) = Action.async {

    implicit val timeout = akka.util.Timeout(5 seconds)

    (lbManager ? UpdateBackendServerWeight(weight, containerVrn, id)).map {
      case LbSuccess =>
        DB.withSession( implicit s => {
          DockerContainers.updateWeightByVrn(vrn = containerVrn, weight = weight)
          Ok
        })
      case LbFail => NotFound
    }

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
