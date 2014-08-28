package controllers

import play.api.mvc._

object Mesos extends Controller {

  //endpoint for callbacks from the Mesos eventbus
  def cb = Action { request =>
    println(request.body.asText)
    Ok
  }
}
