package controllers

import play.api.libs.EventSource
import akka.pattern.ask
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Concurrent
import play.api.mvc.{Action, Controller}
import models.{GetMetrics, MetricsFeed}
import play.api.Play.current
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object FeedsController extends Controller {

  val lbMetricsFeed = Akka.system.actorSelection("akka://application/user/feedsParent/lbMetricsFeed")

  /**
   * lbMetricsFeed returns an SSE feed of load balancer metrics in JSON format
   * @param metricType the type of metric you want to filter on. This can be "backend", "frontend".
   *
   */
  def lbFeed(metricType: Option[String] = None) = Action.async {

    implicit val timeout = akka.util.Timeout(5 seconds)

    (lbMetricsFeed ? GetMetrics).map {
      case MetricsFeed(out) => Ok.feed(out
        &> Concurrent.buffer(60)
        &> EventSource()).as("text/event-stream")
    }
  }

}

