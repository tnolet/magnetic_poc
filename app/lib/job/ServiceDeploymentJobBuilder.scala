package lib.job

import lib.util.date.TimeStamp
import models.service.ServiceCreate
import models.{Job, Jobs}
import play.api.db.slick._
import play.api.libs.json.{ JsValue, Json}
import play.api.Play.current


/**
 * The ServiceDeploymentJobBuilder should be used to create deployment jobs for services.
 * It will create the job object and insert it into the database
 */
class ServiceDeploymentJobBuilder {

  private var jobId: Long = _
  private var service: ServiceCreate = _
  private val priority = 1

  def setService(service: ServiceCreate) = {

    this.service = service

  }


  def build: Long = {

    val timestamp = TimeStamp.now

    val job = new Job(
      Option(0),                        // temporary id, will be discarded
      Jobs.status("new"),               // status
      priority, // priority
      Json.stringify(payload(service)), // payload
      Jobs.queue("serviceDeployment"),  // queue
      timestamp,                        // created timestamp
      timestamp)                        // updated timestamp

    DB.withTransaction { implicit session =>
      jobId = Jobs.insert(job)
    }
    jobId
  }

  /**
   * payload creates the actual payload for the job. This is JSON String because we don't want to store serialized
   * objects in the database
   * @param service the service  to deploy in [[models.service.ServiceCreate]] format
   * @return a string of JSON
   */
  private def payload(service : ServiceCreate) : JsValue = {

    import models.service.ServiceJson.ServiceWritesforCreate

    Json.toJson(service)
  }

}
