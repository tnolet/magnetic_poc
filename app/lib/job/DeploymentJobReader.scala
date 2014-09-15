package lib.job

import models.Job
import models.docker.DockerImage
import play.api.Logger
import play.api.libs.json.Json

/**
 * The DeploymentJobReader should be used to read deployment jobs from the database.
 */
class DeploymentJobReader {

  import models.docker.DockerImageJson.imageReads


  private var _image : DockerImage = _
  private var _service : String = _
  private var _priority : Int = _

  def read(job: Job) : Unit = {

    // parse the payload
    val payload = Json.parse(job.payload)

    // assign parts of the payload to variables
    (payload \ "image").validate[DockerImage].fold(
    valid = {
      image => {
        _image = image
      }
    },
    invalid = {
      errors => Logger.error(s"Invalid image in payload of job ${job.id}. Errors: " + errors)
      }
    )
    _service = (payload \ "service").as[String]

    _priority = job.priority

  }


  def image : DockerImage = {
    _image
  }

  def service : String = {
    _service
  }

  def priority : Int = {
    _priority
  }
}
