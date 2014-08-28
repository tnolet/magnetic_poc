package controllers

import play.api.mvc._
import play.api.libs.json._


object Application extends Controller {

  def index = Action {
    Redirect(routes.Images.list)
  }

  def catchAll(path: String) = Action { request =>
    val response: JsValue = Json.obj("message" -> "URI not found: ".concat(request.uri))
    NotFound(Json.toJson(response))
  }

}