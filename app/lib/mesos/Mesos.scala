package lib.mesos

import com.typesafe.config.ConfigFactory
import play.api.libs.json.JsValue
import play.api.libs.ws.WS
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
/**
 * Created by tim on 29/08/14.
 *
 * This lib contains all basic functions for communicating with Mesos
 */

//case class Mesos(host: String, port: Int, baseUri: String)
object Mesos {

  import play.api.Play.current

  val conf = ConfigFactory.load()
  val mesosHost = conf.getString("mesos.host")
  val mesosPort = conf.getInt("mesos.port")
  val mesosBaseUri = (s"http://$mesosHost:$mesosPort")

  def Health : Future[Int] = {
    WS.url(mesosBaseUri + "/health").get().map {
      case response => response.status
    }
  }

  def host = mesosHost
  def port = mesosPort
  def uri =  mesosBaseUri

  /**
   * Returns an array of all the slaves in the Mesos cluster together with their details
   */
  def slaves : Future[JsValue] = {
    WS.url(s"$mesosBaseUri/master/state.json").get().map {
      case response => (response.json \ "slaves")
    }
  }
}
