package controllers

import models.docker.{DockerImage, DockerImages}
import models.{Job,Jobs}
import play.api.db.slick._
import play.api.mvc._
import play.api.Play.current
import play.api.libs.json._
import lib.util.date._

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

  def deploy(id: Long, amount: Option[Int], environment: Option[Int]) = DBAction { implicit rs =>
    val _image = DockerImages.findById(id)
      _image match {
        case Some(image) =>

          val jobId = createDeployJob(image)
          Created(s"jobId: $jobId ")

        case None => NotFound("No such image found")
      }

  }

  def delete(id: Long) = DBAction { implicit rs =>
    DockerImages.delete(id)
    NoContent
  }

  /**
   * createDeployJob creates a deployment job based on an image and returns the id of the created job
   * @param image is an object of the type [[DockerImage]]
   */


  def createDeployJob(image: DockerImage) : Long = {

    var newJobId: Long = 0

    play.api.db.slick.DB.withTransaction { implicit session =>

      val timestamp = TimeStamp.now

     newJobId = Jobs.insert(new Job(
        Option(0), // temporary id, will be discarded
        Jobs.status("new"), // status
        1, // priority
        Json.stringify(Json.toJson(image)), // payload
        Jobs.queue("deployment"), // queue
        timestamp, // created timestamp
        timestamp) // updated timestamp
      )
    }
    newJobId
  }
}
