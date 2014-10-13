package controllers

import play.api.libs.json.JsValue
import play.api.mvc.{Action, Controller}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import play.api.libs.json._
import play.api.Play.current


object LoadBalancer extends Controller {

  def stats = Action.async {

    val futureStats : Future[JsValue] = lib.loadbalancer.LoadBalancer.getStats
    futureStats.map {
      case stats: JsValue => Ok(Json.toJson(stats))
      case _ => InternalServerError("Could not get loadbalancer stats")
    }
  }
}
