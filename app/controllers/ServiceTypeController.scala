package controllers

import models.service.{ServiceTypes, ServiceType}
import models.service.Services
import play.api.db.slick.DBAction
import play.api.libs.json.{Json, JsError}
import play.api.db.slick._
import play.api.mvc._


object ServiceTypeController extends Controller {

  import models.service.ServiceTypeJson.ServiceTypeReads
  import models.service.ServiceTypeJson.ServiceTypeWrites

  def list = DBAction { implicit rs =>
    val serviceTypes = ServiceTypes.all
    Ok(Json.toJson(serviceTypes))
  }

  def find_by_id(id: Long) = DBAction { implicit rs =>

    val serviceType = ServiceTypes.findById(id)

    serviceType match {

      case Some(serviceType: ServiceType) => Ok(Json.toJson(serviceType))
      case None => NotFound("No service type found")

    }

  }

  def find_services_by_servicetype_id(id: Long) = DBAction { implicit rs =>

    import models.service.ServiceJson.ServiceResultWrites

    val services = Services.findByServiceTypeId(id)
    Ok(Json.toJson(services))

  }

  def find_services_by_id (serviceId: Long, instanceId: Long) = DBAction { implicit rs =>

    import models.service.ServiceJson.ServiceWrites

    val instance = Services.findById(instanceId)
    Ok(Json.toJson(instance))
  }

  def create = DBAction(parse.json) { implicit rs =>
    rs.request.body.validate[ServiceType].fold(
      valid = { serviceType =>
        val servId = ServiceTypes.insert(serviceType)
        Created(s"serviceTypeId: $servId")
      },
      invalid = {
        errors => BadRequest(Json.toJson(JsError.toFlatJson(errors)))
      }
    )
  }

}
