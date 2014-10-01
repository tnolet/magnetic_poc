package lib.job

import models.Job
import models.docker.DockerContainer
import play.api.libs.json.{JsValue, Json}

/**
 * Reads scale jobs
 */
class ScaleJobReader {


  private var _container : DockerContainer = _
  private var _serviceVrn : String = _
  private var _instanceAmount : Long = _
  private var _priority : Int = _
  private var _scaleTypeString : String = _
  private var _scaleType : ScaleType = _

  def read(job: Job) : Unit = {

    import models.docker.DockerContainerJson.containerReads

    // parse the payload
    val payload = Json.parse(job.payload)

    _scaleTypeString = (payload \ "scaleType").as[String]
    val parameters = (payload \ "parameters").as[JsValue]

    _scaleTypeString match {

      case "horizontal" =>
        // assign parts of the payload to variables
        _serviceVrn = (parameters  \ "serviceVrn").as[String]
        _container = (parameters  \ "container").as[DockerContainer]
        _instanceAmount = (parameters \ "instanceAmount").as[Long]
        _priority = job.priority

        _scaleType = Horizontal(_serviceVrn,_container,_instanceAmount)
    }
  }

  def scaleType : ScaleType = {
    _scaleType
  }
}
