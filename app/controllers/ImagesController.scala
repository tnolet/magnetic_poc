package controllers

import models.{DockerImage, DockerImages,Job,Jobs}
import play.api.db.slick._
import play.api.mvc._
import play.api.Play.current
import play.api.libs.json._
import lib.util.date._

object ImagesController extends Controller {


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

  def deploy(id: Long, amount: Option[Int]) = DBAction { implicit rs =>
    val image = DockerImages.findById(id)
      image match {
        case Some(image) => {
          val timestamp = TimeStamp.now
          Jobs.insert( new Job(Option(0),"NEW",1,Json.stringify(Json.toJson(image)),timestamp,timestamp))
          Created("Deployment job created")
        }
        case None => NotFound("No such image found")
      }

  }

  def delete(id: Long) = DBAction { implicit rs =>
    val result = DockerImages.delete(id)
    NoContent
  }
}

