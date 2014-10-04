package lib.job

import models.Job
import models.service.ServiceCreate
import play.api.libs.json.Json
import play.api.Logger

class ServiceDeploymentJobReader {

  import models.service.ServiceJson.ServiceReadsforCreate

  private var _service : ServiceCreate = _
  private var _priority : Int = _

  def read(job: Job) : Unit = {

    // parse the payload
    Json.parse(job.payload)
      .validate[ServiceCreate]
      .fold(
        valid = { service => {
          _service = service
        }
      },
      invalid = {
        errors => Logger.error(s"Invalid service in the payload of job ${job.id}. Errors: " + errors)
      }
    )
    _priority = job.priority

  }
  def service : ServiceCreate = {
    _service
  }

}
