package controllers

import lib.mesos.Mesos
import play.api.libs.json.{Json, JsValue}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc.{Action, Controller}


/**
 * The system controller provides endpoints to general system information
 */
object SystemController extends Controller {

  /**
   * mesosSlaves returns the current slaves in Mesos
   */
  def mesosSlaves = Action.async {

    val futureResult : Future[JsValue] = Mesos.slaves

    futureResult.map {
      case json: JsValue => Ok(Json.toJson(json))

  }

  }

  /**
   * mesosMetrics returns the current metrics for the Mesos cluster
   */
  def mesosMetrics = Action.async {

    val futureResult: Future[JsValue] = Mesos.metrics

    futureResult.map {
      case json: JsValue => Ok(Json.toJson(json))

    }
  }
}
