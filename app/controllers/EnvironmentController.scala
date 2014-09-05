package controllers

import play.api.db.slick.DBAction
import play.api.mvc.Controller
import play.api.libs.json._
import models.{Environments, Environment}
import play.api.Play.current
import play.api.db.slick._

object EnvironmentController extends Controller {

  // Json reading/writing
  implicit val envReads = Json.reads[Environment]
  implicit val envWrites = Json.writes[Environment]

  def list = DBAction { implicit rs =>
    val job = Environments.all
    Ok(Json.toJson(job))
  }

  def find_by_id(id: Long) = DBAction { implicit rs =>
    val job = Environments.findById(id)
    Ok(Json.toJson(job))
  }
}
