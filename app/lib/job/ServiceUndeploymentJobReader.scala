package lib.job

import models.Job
import models.service.Service
import play.api.Logger
import play.api.libs.json.Json

/**
 * The ServiceUnDeploymentJobReader should be used to read service undeployment jobs from the database.
 */
class ServiceUndeploymentJobReader {

  private var _service : Service = _

  def read(job: Job) : Unit = {

    import models.service.ServiceJson.ServiceReads

    Json.parse(job.payload)
      .validate[Service]
      .fold(
        valid = { service => {
          _service = service
        }
        },
        invalid = {
          errors => Logger.error(s"Invalid service in payload of job ${job.id}. Errors: " + errors)
        }
      )

  }

  def service : Service = {
    _service
  }

}
