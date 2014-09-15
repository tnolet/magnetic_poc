package lib.job

import lib.util.date.TimeStamp
import models.{Job, Jobs}
import models.docker.DockerContainer
import play.api.db.slick._
import play.api.libs.json.Json
import play.api.Play.current

/**
 * The UnDeploymentJobBuilder should be used to create undeployment jobs. You can do it by hand, but this is easier.
 * It will create the job object and insert it into the database
 */
class UnDeploymentJobBuilder {

  import models.docker.DockerContainerJson.containerWrites

  private var jobId : Long = _
  private var container : DockerContainer = _


  def setContainer(container: DockerContainer) = {
    this.container = container
  }


  def build : Long = {

    val timestamp = TimeStamp.now

    val job = new Job(
    Option(0),                                // temporary id, will be discarded
    Jobs.status("new"),                       // status
    1,                                        // priority
    Json.stringify(Json.toJson(container)),   // payload
    Jobs.queue("undeployment"),               // queue
    timestamp,                                // created timestamp
    timestamp)                                // updated timestamp


    DB.withTransaction { implicit session =>
      jobId = Jobs.insert(job)
    }
    jobId

  }

}
