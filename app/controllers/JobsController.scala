package controllers

import models.{Jobs, Job}
import play.api.db.slick.DBAction
import play.api.libs.json.Json
import play.api.mvc.Controller
import play.api.libs.json._
import play.api.libs.functional.syntax._
import java.sql.Timestamp
import play.api.Play.current
import play.api.db.slick._



/**
 * JobsController provides the REST interface to all Jobs
 *
 */


object JobsController extends Controller {

  // Json reading/writing
  implicit val jobWrites = Json.writes[Job]

  implicit val jobReads = (
    (__ \ 'id).read[Option[Long]] and
      (__ \ 'status).read[String] and
      (__ \ 'priority).read[Int] and
      (__ \ 'payload).read[String] and
      (__ \ 'created_at).read[Long].map{ long => new Timestamp(long) } and
      (__ \ 'updated_at).read[Long].map{ long => new Timestamp(long) }
    )(Job)


  def list = DBAction { implicit rs =>
    val job = Jobs.all
    Ok(Json.toJson(job))
  }

  def find_by_id(id: Long) = DBAction { implicit rs =>
    val job = Jobs.findById(id)
    Ok(Json.toJson(job))
  }
}
