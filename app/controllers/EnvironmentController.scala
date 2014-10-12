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

  import models.service.ServiceJson.ServiceResultWrites

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

        val services : List[ServiceResult] = Services.findByEnvironmentId(env.id.get)
        val envResult  = EnvironmentResult(env.id,env.name,env.state,services)
        Ok(Json.toJson(envResult))

      case None => NotFound("No such environment found")
    }


  }
}
