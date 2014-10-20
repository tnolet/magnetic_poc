package controllers

import lib.kairosdb.KairosDB
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.ws.WSResponse


object FeedsController extends Controller {

  /**
   * lbMetricsFeed returns a set of load balancer metrics in JSON format
   */
  def lbFeed(metric: String, proxy: String, proxyType: String, relativeTime: Int, timeUnit: String) = Action.async {

     val futureResult : Future[WSResponse] = KairosDB.getMetrics(metric, proxy, proxyType, relativeTime, timeUnit)

    futureResult.map {
      case s if s.status < 399 => Ok(Json.toJson(s.json))
      case s if s.status > 399 => InternalServerError("Could not get metrics feed")
    }

  }
  // WIP
  // todo: find nice way to report just one metric value
  def lbDataPoint(metric: String, proxy: String, proxyType: String) = Action {

    KairosDB.getDataPoint(metric, proxy, proxyType)
    Ok

  }

}

