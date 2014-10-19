package lib.kairosdb

import com.typesafe.config.ConfigFactory
import models.kairosdb.DataPoint
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{WSResponse, WS}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


/**
 * Handles the connection to the Kairos DB used for storing time series metrics
 */
object KairosDB {

  import play.api.Play.current

  val conf = ConfigFactory.load()

  val kdHost = conf.getString("kairosdb.host")
  val kdApiPort = conf.getInt("kairosdb.port")
  val kdApiVersion = conf.getString("kairosdb.api.version")

  val kdApi = s"http://$kdHost:$kdApiPort/api/v$kdApiVersion"

  def host = kdHost
  def port = kdApiPort
  def uri = kdApi
  /**
   * get the /version endpoint for basic health checking
   * @return
   */
  def Health : Future[Int] = {
    WS.url(kdApi + "/version").get().map {
      case response => response.status
    }
  }

  /**
   * Set a datapoint in KairosDB
   * @param dataPoint object of the type [[DataPoint]]
   * @return a boolean indicating success of failureof setting the point
   */
  def setDataPoint(dataPoint: DataPoint): Future[Boolean] = {

    import models.kairosdb.DataPointJson.datapointWrites

    val json = Json.toJson(dataPoint)

    WS.url(s"$kdApi/datapoints").post(json).map {

      case response =>
        response.status < 399
    }
  }

  def getMetrics(metric: String, proxy: String, proxyType: String, relativeTime: Int, timeUnit: String) : Future[WSResponse] = {

    val tags : JsValue = Json.obj(
      "source" -> "loadbalancer",
      "metric" -> metric,
      "proxy" -> proxy,
      "proxyType" -> proxyType
    )

    val fullMetricName = s"$proxy.$proxyType.$metric"


    val _metric : JsValue = Json.obj(
      "tags" -> tags,
      "name" -> fullMetricName

    )

    val query : JsValue = Json.obj(
//      "cache_time" -> 10  ,
      "start_relative" -> Json.obj(
        "value" -> relativeTime,
        "unit" -> timeUnit
      ),
      "metrics" -> Json.arr(_metric)
    )

    val json = Json.toJson(query)

    WS.url(s"$kdApi/datapoints/query").post(json).map {

      case response =>
        response
    }

  }


}
