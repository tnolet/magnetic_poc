package controllers

import play.api.mvc.{Action, Controller}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import play.api.libs.json._


object LoadBalancer extends Controller {

  def stats = Action.async {

    val futureStats : Future[List[JsObject]] = lib.loadbalancer.LoadBalancer.getStats()
    futureStats.map {
      case stats => Ok(Json.toJson(stats))
      case _ => InternalServerError("Could not get loadbalancer stats")
    }
  }
}
