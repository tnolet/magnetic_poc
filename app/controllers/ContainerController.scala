package controllers


import lib.job.{Horizontal, ScaleJobBuilder, UnDeploymentJobBuilder}
import models.docker._
import play.api.db.slick._
import play.api.libs.concurrent.Akka
import play.api.libs.json._
import play.api.mvc._
import play.api.Play.current

object ContainerController extends Controller {

  val lbManager = Akka.system.actorSelection("akka://application/user/lbManager")


  import models.docker.DockerContainerJson.containerReads
  import models.docker.DockerContainerJson.containerWrites

  def list = DBAction { implicit rs =>
    val containers = DockerContainers.all
    Ok(Json.toJson(containers))
  }

  def find_by_id(id: Long) = DBAction { implicit rs =>

    import models.docker.DockerContainerJson.containerResultWrites

    val container = DockerContainers.findById(id)

    container match {
      case Some(cnt: DockerContainer) =>

        val instances : List[ContainerInstance] = ContainerInstances.findByContainerId(id)

        if (instances.nonEmpty) {

          // combine the instances and the container to a result
          val contResult = DockerContainerResult.createResult(cnt, instances)

          Ok(Json.toJson(contResult))

        } else { NotFound(s"No instances found for container ${cnt.vrn}") }

      case None => NotFound("No such container found")
    }


  }

  def find_config_by_id(id: Long) = DBAction {implicit rs =>

    import models.docker.ContainerInstanceJson.instanceWrites

    val container = DockerContainers.findById(id)
    container match {
      case Some(cnt: DockerContainer) =>

        val config = ContainerInstances.findByContainerId(id)
        Ok(Json.toJson(config))

      case None => NotFound("No such container found")
    }
  }


  /**
   * Todo: Deletes a container, when it is eligable to be deleted. This means:
   * - it should not be in the destroyed state already
   * - it should not be part of running/live service
   * @param id The id of the container
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
