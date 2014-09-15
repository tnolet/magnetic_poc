package lib.job

import models.Job
import models.docker.DockerContainer
import play.api.Logger
import play.api.libs.json.Json

/**
 * The UnDeploymentJobReader should be used to read undeployment jobs from the database.
 */
class UnDeploymentJobReader {

  private var _vrn : String = _

  def read(job: Job) : Unit = {

    import models.docker.DockerContainerJson.containerReads

    Json.parse(job.payload)
      .validate[DockerContainer]
      .fold(
        valid = { container => {

          _vrn = container.vrn
        }
        },
        invalid = {
          errors => Logger.error(s"Invalid container in payload of job ${job.id}. Errors: " + errors)
        }
      )

  }

  def vrn : String = {
    _vrn
  }

}
