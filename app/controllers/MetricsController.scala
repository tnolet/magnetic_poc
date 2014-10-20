package controllers

import lib.kairosdb.KairosDB
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, Controller}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.ws.WSResponse


object MetricsController extends Controller {

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
  // todo: find nice way to report just one metric value from KairosDB
  // for now, must pulling it straight from the loadbalancer and parsing it to get the right data

  /**
   * Gets a snapshot of the metrics from the loadbalancer for specific service.
   * The snapshot consists of frontend and backend metrics from the loadbalancer.
   * @param vrn the vrn of the [[models.service.Service]]
   * @return a Json array with frontend and backend metrics
   */
  def getServiceSnapshot(vrn: String) = Action.async {

    for {
      frontend <- lib.loadbalancer.LoadBalancer.getStats(Some("frontend"))
      backend <- lib.loadbalancer.LoadBalancer.getStats(Some("backend"))
    } yield {

      val feMetric = for (
        m <- frontend if m.\("pxname").as[String] == vrn
      ) yield m

      val beMetric = for (
        m <- backend if m.\("pxname").as[String] == vrn
      ) yield m

      val totalMetrics = feMetric.++(beMetric)
      Ok(Json.toJson(totalMetrics ))

    }
  }

  /**
   * Gets a snapshot of the metric from the loadbalancer for a specific server
   * @param vrn [[models.loadbalancer.BackendServer]]
   * @return
   */
  def getServerSnapshot(vrn: String) = Action.async {

    for {
      server <- lib.loadbalancer.LoadBalancer.getStats(Some("server"))
    } yield {

      val srvMetric = for (
        m <- server if m.\("svname").as[String] == vrn
      ) yield m


      val totalMetrics = srvMetric
      Ok(Json.toJson(totalMetrics))

    }
  }
}

