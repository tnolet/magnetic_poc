package lib.job

import lib.util.date.TimeStamp
import models.{Job, Jobs}
import models.service.Service
import play.api.db.slick._
import play.api.libs.json.Json
import play.api.Play.current

/**
 * The ServiceUnDeploymentJobBuilder should be used to create service undeployment jobs.
 * It will create the job object and insert it into the database
 */
class ServiceUndeploymentJobBuilder {

  import models.service.ServiceJson.ServiceWrites

  private var jobId : Long = _
  private var service : Service = _


  def setService(service: Service) = {
    this.service = service
  }


  def build : Long = {

    val timestamp = TimeStamp.now

    val job = new Job(
      Option(0),                                // temporary id, will be discarded
      Jobs.status("new"),                       // status
      1,                                        // priority
      Json.stringify(Json.toJson(service)),     // payload
      Jobs.queue("serviceUnDeployment"),        // queue
      timestamp,                                // created timestamp
      timestamp)                                // updated timestamp


    DB.withTransaction { implicit session =>
      jobId = Jobs.insert(job)
    }
    jobId

  }

}
