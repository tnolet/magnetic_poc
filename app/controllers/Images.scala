package controllers

import actors.Stage
import akka.util.Timeout
import models.{DockerImage, DockerImages}
import play.api.db.slick._
import play.api.libs.concurrent.Akka
import play.api.mvc._
import play.api.Play.current
import play.api.libs.json._
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global




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

  def find_by_id(id: Long) = DBAction { implicit rs =>
    val image = DockerImages.findById(id)
    Ok(Json.toJson(image))
  }


  def find_by_name(name: String) = DBAction { implicit rs =>
    val image = DockerImages.findByName(name)
    Ok(Json.toJson(image))
  }


  def create = DBAction(parse.json) { implicit rs =>
    rs.request.body.validate[DockerImage].fold(
      valid = { image =>
        DockerImages.insert(image)
        Created
      },
      invalid = {
        errors => BadRequest(Json.toJson(JsError.toFlatJson(errors)))
      }
    )
  }

  def deploy(id: Long) = Action.async {
    val deployer = Akka.system.actorSelection("akka://application/user/deployer")
    implicit val timeout = Timeout(5 seconds)
    (deployer ? Stage(id)).mapTo[String].map( response => Ok(response))
  }

  def delete(id: Long) = DBAction { implicit rs =>
    val result = DockerImages.delete(id)
    NoContent
  }
}

