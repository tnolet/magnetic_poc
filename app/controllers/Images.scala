package controllers

import models.{DockerImage, DockerImages}
import play.api.db.slick._
import play.api.mvc._
import play.api.Play.current
import play.api.libs.json._

object Images extends Controller {

//  def list = Action.async {
//    WS.url("http://" + docker_host + ":" + docker_port + "/images/json").withRequestTimeout(5000)
//      .get().map {
//      response =>
//        Ok(response.json);
//    }
// }

  // Json reading/writing
  implicit val imageReads = Json.reads[DockerImage]
  implicit val imageWrites = Json.writes[DockerImage]


  def list = DBAction { implicit rs =>
    val images = DockerImages.all
    Ok(Json.toJson(images))
  }

  def find(id: Long) = DBAction { implicit rs =>
    val image = DockerImages.findById(id)
    Ok(Json.toJson(image))
  }

  def create = DBAction(parse.json) { implicit rs =>
    val body = rs.request.body
    body.validate[DockerImage].fold(
      valid = { image =>
        DockerImages.insert(image)
        Ok("Ok")
      },
      invalid = {
        errors => BadRequest(JsError.toFlatJson(errors))
      }
    )
  }
}

