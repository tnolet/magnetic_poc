package controllers

import java.sql.Timestamp

import lib.util.date.TimeStamp
import models.{Job, Jobs}
import models.docker.{DockerContainer, DockerContainers}
import play.api.db.slick._
import play.api.libs.json._
import play.api.mvc._
import play.api.libs.functional.syntax._
import play.api.Play.current

object ContainerController extends Controller {

  // Json reading/writing
  implicit val containerWrites = Json.writes[DockerContainer]

  implicit val containerReads = (
   (__ \ 'id).read[Option[Long]] and
      (__ \ 'vrn).read[String] and
      (__ \ 'status).read[String] and
      (__ \ 'imageRepo).read[String] and
      (__ \ 'imageVersion).read[String] and
      (__ \ 'ports).read[String] and
      (__ \ 'environmentId).read[Long] and
      (__ \ 'created_at).read[Long].map{ long => new Timestamp(long) }
    )(DockerContainer)

  def list = DBAction { implicit rs =>
    val containers = DockerContainers.all
    Ok(Json.toJson(containers))
  }

  def find_by_id(id: Long) = DBAction { implicit rs =>
    val container = DockerContainers.findById(id)
    Ok(Json.toJson(container))
  }

  /**
   * Deletes a container, when it is eligable to be deleted. This means it should not be in the destroyed state already
   * @param id The id of the containers
   */

  def delete(id: Long) = DBAction { implicit rs =>
    val _container = DockerContainers.findById(id)
    _container match {
      case Some(container) =>

        val jobId = createDestroyJob(container)
        Created(s"jobId: $jobId ")

      case None => NotFound("No such container found")
    }

  }

  def create = DBAction(parse.json) { implicit rs =>
    rs.request.body.validate[DockerContainer].fold(
      valid = { image =>
        val contId = DockerContainers.insert(image)
        Created(s"containerId: $contId ")
      },
      invalid = {
        errors => BadRequest(Json.toJson(JsError.toFlatJson(errors)))
      }
    )
  }

  /**
   * createDestroyJob creates a destroy job for a container and returns the id of the created job
   * @param container is an object of the type [[DockerContainer]]
   */


  def createDestroyJob(container: DockerContainer) : Long = {

    var newJobId: Long = 0

    play.api.db.slick.DB.withTransaction { implicit session =>

      val timestamp = TimeStamp.now

      newJobId = Jobs.insert(new Job(
        Option(0), // temporary id, will be discarded
        Jobs.status("new"), // status
        1, // priority
        Json.stringify(Json.toJson(container)), // payload
        Jobs.queue("undeployment"), // queue
        timestamp, // created timestamp
        timestamp) // updated timestamp
      )
    }
    newJobId
  }

}
