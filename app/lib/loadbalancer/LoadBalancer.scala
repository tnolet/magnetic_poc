package lib.loadbalancer

import com.typesafe.config.ConfigFactory
import models.loadbalancer._
import play.api.libs.json.Json
import play.api.libs.ws.WS
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by tim on 04/09/14.
 */
object LoadBalancer {

  import play.api.Play.current

  val conf = ConfigFactory.load()
  val lbHost = conf.getString("loadbalancer.host")
  val lbApiPort = conf.getInt("loadbalancer.api.port")
  val lbApiVersion = conf.getString("loadbalancer.api.version")

  val lbApi = s"http://$lbHost:$lbApiPort/v$lbApiVersion"

  def getConfig: Future[Configuration] = {
    WS.url(s"$lbApi/config").get().map {
      case response => {
        response.json
          .validate[Configuration].get
      }
    }
  }

  def setConfig(config: Configuration): Unit = {

    val json = Json.toJson(config)
    WS.url(s"$lbApi/config").post(json)
  }
}
