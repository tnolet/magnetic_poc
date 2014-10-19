package controllers

import lib.kairosdb.KairosDB
import play.api.libs.concurrent.Akka
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.ws.WSResponse


object FeedsController extends Controller {

  val lbMetricsFeed = Akka.system.actorSelection("akka://application/user/feedsParent/lbMetricsFeed")

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

}

