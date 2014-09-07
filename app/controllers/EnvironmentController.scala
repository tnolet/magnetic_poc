package controllers

import play.api.db.slick.DBAction
import play.api.mvc.Controller
import play.api.libs.json._
import models. Environments
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
    val env = Environments.findById(id)
    Ok(Json.toJson(env))
  }
}
