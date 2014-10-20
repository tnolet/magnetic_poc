package lib.kairosdb

import com.typesafe.config.ConfigFactory
import models.kairosdb.DataPoint
import play.api.Logger
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

  /**
   *
   * Gets a series of metrics from KairosDB
   *
   * @param metric the metric name, i.e "scur", "rate_max"
   * @param proxy the name of the proxy, normally a vrn
   * @param proxyType the type of the proxy, possible values are: "backend", "frontend" , "server"
   * @param relativeTime the relative time since when we want the metric. Related to time unit. Default: 10
   * @param timeUnit the time unit associated with the relative time, i.e. "hours". Default: "minutes"
   * @return
   */
  def getMetrics(metric: String, proxy: String, proxyType: String, relativeTime: Int = 10, timeUnit: String = "minutes") : Future[WSResponse] = {

    val json = Json.toJson(Query(metric, proxy, proxyType, relativeTime, timeUnit))

    WS.url(s"$kdApi/datapoints/query").post(json).map {

      case response =>
        response
    }

  }

  /**
   *
   * get a small set of metrics and just returns the last point. This comes in handy if we just want the last value
   * of a specific metric and don't need the full series of metrics spanning a amount of time.
   * @param metric
   * @param proxy
   * @param proxyType
   */
  def getDataPoint(metric: String, proxy: String, proxyType: String) = {

    val json = Json.toJson(Query(metric, proxy, proxyType, 1, "minutes"))

    WS.url(s"$kdApi/datapoints/query").post(json).map {

      case response =>
        if (response.status < 399 ){
          val values = Json.parse(response.body) \\ "values"
          Logger.info(values(0) .toString())

        }
    }


  }

  private def Query(metric: String, proxy: String, proxyType: String, relativeTime: Int, timeUnit: String) : JsValue ={

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
      "start_relative" -> Json.obj(
        "value" -> relativeTime,
        "unit" -> timeUnit
      ),
      "metrics" -> Json.arr(_metric)
    )

    query

  }


}
