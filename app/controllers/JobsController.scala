package controllers

import models.{JobEvents, Jobs}
import play.api.db.slick.DBAction
import play.api.libs.json.Json
import play.api.mvc.Controller
import play.api.Play.current
import play.api.db.slick._

/**
 * JobsController provides the REST interface to all Jobs
 */

object JobsController extends Controller {

  import models.JobJson.jobWrites

  def list = DBAction { implicit rs =>
    val job = Jobs.all
    Ok(Json.toJson(job))
  }

  def find_by_id(id: Long) = DBAction { implicit rs =>

    val job = Jobs.findById(id)
    Ok(Json.toJson(job))
  }

  def find_events_by_id(id: Long) = DBAction { implicit rs =>

    import models.JobEventJson.jobEventWrites

    val events = JobEvents.findByJobId(id)
    Ok(Json.toJson(events))

  }
}
