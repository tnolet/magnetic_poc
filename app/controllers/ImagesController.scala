package controllers

import lib.job.DeploymentJobBuilder
import models.docker.{DockerImage, DockerImages}
import play.api.db.slick._
import play.api.mvc._
import play.api.Play.current
import play.api.libs.json._

object ImagesController extends Controller {

  import models.docker.DockerImageJson.imageReads
  import models.docker.DockerImageJson.imageWrites

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

  def deploy(id: Long, service: Option[String]) = DBAction { implicit rs =>
    val _image = DockerImages.findById(id)
      _image match {
        case Some(image) =>

          // create a builder and build a new deployment job
          val builder = new DeploymentJobBuilder
          builder.setImage(image)
          builder.setService(service.getOrElse("dummyservice"))
          builder.setPriority(1)
          val jobId = builder.build
          Created(Json.toJson(Json.obj("jobId" -> JsNumber(jobId))))

        case None => NotFound("No such image found")
      }
  }

  def delete(id: Long) = DBAction { implicit rs =>
    DockerImages.delete(id)
    NoContent
  }
}
