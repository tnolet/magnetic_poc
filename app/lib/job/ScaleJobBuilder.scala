package lib.job

import lib.util.date.TimeStamp
import models.docker.DockerContainer
import models.{Jobs, Job}
import play.api.db.slick._
import play.api.libs.json.{JsString, JsNumber, JsValue, Json}
import play.api.Play.current



trait ScaleType
case class Horizontal(serviceVrn: String, container : DockerContainer, instanceAmount : Long) extends ScaleType

// not yet implemented
case class Vertical(serviceVrn: String, container : DockerContainer, placeholder : Long) extends ScaleType

/**
 * The ScaleJobBuilder should be used to create scaling jobs. Scale jobs can be of two types:
 * 1. Horizontal, meaning adding instances
 * 2. Vertical, meaning adjusting the cpu/mem/disk of an instance
 */
class ScaleJobBuilder {

  private var jobId : Long = _
  private var scaleType : ScaleType = _
  private val priority = 1


  def setScaleType(scaleType: ScaleType) = {
    this.scaleType = scaleType
  }

  /**
   * Based on the input of the build method, a job with a specific scaling payload is created.
   * @return the id of the created job
   */
  def build : Long = {

    val timestamp = TimeStamp.now

    val job =  new Job(
      Option(0),                                        // temporary id, will be discarded
      Jobs.status("new"),                               // status
      priority,                                         // priority
      Json.stringify(payload(                           // payload
        this.scaleType)
      ),
      Jobs.queue("scaling"),                            // queue
      timestamp,                                        // created timestamp
      timestamp)                                        // updated timestamp

    DB.withTransaction { implicit session =>
      jobId = Jobs.insert(job)
    }
    jobId
  }

  private def payload(scaleType : ScaleType) : JsValue = {

    import models.docker.DockerContainerJson.containerWrites

   scaleType match {
      case (st : Horizontal) =>
        Json.obj(
        "scaleType" -> JsString("horizontal"),
        "parameters" -> Json.obj(
          "serviceVrn" -> JsString(st.serviceVrn),
          "container" -> Json.toJson(st.container),
          "instanceAmount" -> JsNumber(st.instanceAmount)
        )
        )
    }
  }
}
