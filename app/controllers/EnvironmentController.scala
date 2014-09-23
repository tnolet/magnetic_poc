package controllers

import models.docker._
import models.service.{ServiceResult, Service, Services}
import play.api.db.slick.DBAction
import play.api.mvc.Controller
import play.api.libs.json._
import models.{EnvironmentResult, Environments, Environment}
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

    val _env = Environments.findById(id)

    _env match {

      case Some(env : Environment) =>

        val services : List[Service] = Services.findByEnvironmentId(env.id.get)

        val servicesWithContainers : List[ServiceResult] = services.map( srv => {

          val containers : List[DockerContainer] =  DockerContainers.findByServiceId(srv.id.get)

          val containersResult : List[DockerContainerResult] = containers.map (cnt => {
             val _instance =  ContainerInstances.findByContainerId(cnt.id.get)
            _instance match {
              case Some(instance : ContainerInstance) =>
                DockerContainerResult(cnt.id, cnt.vrn, cnt.status, cnt.imageRepo, cnt.imageVersion, cnt.ports, cnt.serviceId, instance, cnt.created_at)
            }
          })
          ServiceResult(srv.id,srv.port,srv.state, srv.vrn,srv.serviceTypeId,containersResult)
          }
        )

        val envResult  = EnvironmentResult(env.id,env.name,env.state,servicesWithContainers)
        Ok(Json.toJson(envResult))

      case None => NotFound("No such environment found")
    }


  }
}
