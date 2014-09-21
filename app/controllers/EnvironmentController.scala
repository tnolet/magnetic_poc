package controllers

import models.docker.{DockerContainers, DockerContainer}
import models.service.{ServiceResult, Service, Services}
import play.api.db.slick.DBAction
import play.api.mvc.Controller
import play.api.libs.json._
import models.{EnvironmentResult, Environments}
import play.api.Play.current
import play.api.db.slick._
import play.api.libs.functional.syntax._

object EnvironmentController extends Controller {

  import models.EnvironmentJson.envWrites

  def list = DBAction { implicit rs =>
    val envs = Environments.all

    Ok(Json.toJson(envs))
  }

  def find_by_id(id: Long) = DBAction { implicit rs =>

    import models.EnvironmentJson.envResultWrites

    val env = Environments.findById(id).get
    val services : List[Service] = Services.findByEnvironmentId(env.id.get)

    val servicesWithContainers : List[ServiceResult] = services.map( srv => {
        val containers : List[DockerContainer] =  DockerContainers.findByServiceId(srv.id.get)
        ServiceResult(srv.id,srv.port,srv.state, srv.vrn,srv.serviceTypeId,containers)
      }
    )

    val env2  = EnvironmentResult(env.id,env.name,env.state,servicesWithContainers)
    Ok(Json.toJson(env2))
  }
}
