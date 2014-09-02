package lib.marathon

import com.typesafe.config.ConfigFactory
import play.api.libs.ws.WS
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by tim on 02/09/14.
 *
 * This lib contains all basic functions for communicating with Marathon
 */

object Marathon {

  import play.api.Play.current

  val conf = ConfigFactory.load()
  val marathonHost = conf.getString("marathon.host")
  val marathonPort = conf.getInt("marathon.port")
  val marathonApiVersion = conf.getString("marathon.api.version")

  // Some api calls of Marathon are not under the api path
  val marathonBaseUri = s"http://$marathonHost:$marathonPort"
  val marathonApi = s"http://$marathonHost:$marathonPort/$marathonApiVersion"

  def Ping : Future[Int] = {
    WS.url(marathonBaseUri + "/ping").get().map {
      case response => response.status
    }
  }

  def host = marathonHost
  def port = marathonPort
  def uri = marathonBaseUri
}
