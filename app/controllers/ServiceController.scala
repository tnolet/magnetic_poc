package controllers

import lib.util.date.TimeStamp
import models.docker.DockerContainers
import models.{Job, Jobs}
import models.service.{ServiceCreate, Services, Service}
import play.api.db.slick.DBAction
import play.api.libs.json.{JsError, Json}
import play.api.db.slick._
import play.api.mvc._
import play.api.Play.current


object ServiceController extends Controller {

  import models.service.ServiceJson.ServiceWrites
  import models.service.ServiceJson.ServiceReads


  def list = DBAction { implicit rs =>
    val services = Services.all
    Ok(Json.toJson(services))
  }

  def find_by_id(id: Long) = DBAction { implicit rs =>

    val service = Services.findById(id)

    service match {
      case Some(service: Service) => Ok(Json.toJson(service))
      case None => NotFound("No service found")
    }
  }

  def find_containers_by_id(id: Long) = DBAction { implicit rs =>

    import models.docker.DockerContainerJson.containerWrites

    val _service = Services.findById(id)
    _service match {
      case Some(service) =>
       val containers = DockerContainers.findByServiceId(service.id.get)
        Ok(Json.toJson(containers))

      case None => NotFound("No such service found")
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
