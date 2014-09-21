package lib.loadbalancer

import com.typesafe.config.ConfigFactory
import models.loadbalancer._
import play.api.libs.json.{JsValue, Json}
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

  def host = lbHost
  def port = lbApiPort
  def uri = lbApi


  def Health : Future[Int] = {
    WS.url(lbApi + "/info").get().map {
      case response => response.status
    }
  }

  def getConfig: Future[Option[Configuration]] = {
    WS.url(s"$lbApi/config").get().map {
      case response => {
        response.json
          .validate[Configuration].asOpt
      }
    }
  }

  def setConfig(config: Configuration): Future[Boolean] = {
    val json = Json.toJson(config)
    WS.url(s"$lbApi/config").post(json).map {
      case response => (response.status < 399)
    }
  }


 def getStats : Future[JsValue] = {
   WS.url(lbApi + "/stats").get().map {
     case response => {
       response.json
     }
   }
 }

 // def getWeight
}
