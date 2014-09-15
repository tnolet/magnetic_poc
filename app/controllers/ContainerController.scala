package controllers


import lib.job.UnDeploymentJobBuilder
import models.docker.{DockerContainer, DockerContainers}
import play.api.db.slick._
import play.api.libs.json._
import play.api.mvc._
import play.api.Play.current

object ContainerController extends Controller {

  import models.docker.DockerContainerJson.containerReads
  import models.docker.DockerContainerJson.containerWrites

  def list = DBAction { implicit rs =>
    val containers = DockerContainers.all
    Ok(Json.toJson(containers))
  }

  def find_by_id(id: Long) = DBAction { implicit rs =>
    val container = DockerContainers.findById(id)
    Ok(Json.toJson(container))
  }

  /**
   * Todo: Deletes a container, when it is eligable to be deleted. This means:
   * - it should not be in the destroyed state already
   * - it should not be part of running/live service
   * @param id The id of the containers
   */
  def delete(id: Long) = DBAction { implicit rs =>
    val _container = DockerContainers.findById(id)
    _container match {
      case Some(container) =>

        // create an UnDeploymentJobBuilder
        val builder = new UnDeploymentJobBuilder
        builder.setContainer(container)
        val jobId = builder.build

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
}
