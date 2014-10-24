package controllers

import models._
import play.api.db.slick.DBAction
import play.api.libs.json.{Json,JsError}
import play.api.db.slick._
import play.api.mvc._

object SlaController extends Controller {

  import models.SlaJson.SlaWrites

  def list = DBAction { implicit rs =>
    val slas = Slas.all
    Ok(Json.toJson(slas))
  }

  def find_by_id(id: Long) = DBAction { implicit rs =>

    val serviceType = Slas.findById(id)
    serviceType match {

      case Some(sla: Sla) => Ok(Json.toJson(sla))
      case None => NotFound("No sla found")
    }
  }

  // todo: handle creation and deletion of Sla's through jobs
  def create = DBAction(parse.json) { implicit rs =>

    import models.SlaJson.SlaCreateReads

    rs.request.body.validate[SlaCreate].fold(
      valid = { sla =>
        val newSla = Sla(
        id = Some(0L),
        state = SlaState.NEW.toString,
        metricType = sla.metricType,
        lowThreshold = sla.lowThreshold,
        highThreshold = sla.highThreshold,
        backOffTime = sla.backOffTime,
        backOffStages = sla.backOffStages,
        maxEscalations = sla.maxEscalations,
        vrn = sla.vrn,
        serviceId = sla.serviceId,
        created_at = lib.util.date.TimeStamp.now,
        updated_at = lib.util.date.TimeStamp.now
        )
        val slaId = Slas.insert(newSla)
        Created(s"slaId: $slaId")
      },
      invalid = {
        errors => BadRequest(Json.toJson(JsError.toFlatJson(errors)))
      }
    )
  }

  /**
   * Mark an SLA ready for deletion
   * @param id The id of the sla
   */
  def delete(id: Long) = DBAction { implicit rs =>
    Slas.mark_for_deletion(id)
    NoContent
  }

}
