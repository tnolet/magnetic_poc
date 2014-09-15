package lib.job

import lib.util.date.TimeStamp
import models.{Jobs, Job}
import models.docker.DockerImage
import play.api.libs.json._
import play.api.db.slick._
import play.api.Play.current

/**
 * The DeploymentJobBuilder should be used to create deployment jobs. You can do it by hand, but this is easier.
 * It will create the job object and insert it into the database
 */
class DeploymentJobBuilder {

  import models.docker.DockerImageJson.imageWrites

  private var jobId : Long = _
  private var image : DockerImage = _
  private var service : String = _
  private var priority = 1


  def setImage(image : DockerImage) = {
    this.image = image
  }

  def setService(service : String) = {
    this.service = service
  }

  def setPriority(priority : Int) = {
    this.priority = priority
  }

  /**
   * Based on the input of the build method, a job with a specific deployment payload is created.
   * @return the id of the created job
   */
  def build : Long = {

    val timestamp = TimeStamp.now

    val job =  new Job(
      Option(0),                                        // temporary id, will be discarded
      Jobs.status("new"),                               // status
      priority,                                         // priority
      Json.stringify(payload(image,                     // payload
                             service)
      ),
      Jobs.queue("deployment"),                         // queue
      timestamp,                                        // created timestamp
      timestamp)                                        // updated timestamp

      DB.withTransaction { implicit session =>
        jobId = Jobs.insert(job)
      }
      jobId
    }

  /**
   * payload creates the actual payload for the job. This is JSON String because we don't want to store serialized
   * objects in the database
   * @param image the docker image to deploy in [[models.docker.DockerImage]] format
   * @param service the service to deploy the Docker image to
   * @return a string of JSON
   */
  private def payload(image : DockerImage, service : String) : JsValue = {

    // holder for the whole payload
    val result : JsValue = Json.obj(
    "service" -> JsString(service),
    "image" -> Json.toJson(image)
    )
    result
  }
}
