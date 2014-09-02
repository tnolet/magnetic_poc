package lib.mesos

import com.typesafe.config.ConfigFactory
import play.api.libs.ws.WS
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
/**
 * Created by tim on 29/08/14.
 */
object Mesos {

  import play.api.Play.current

  val conf = ConfigFactory.load()
  val mesosHost = conf.getString("mesos.host")
  val mesosPort = conf.getInt("mesos.port")
  val mesosUri = WS.url(s"http://$mesosHost:$mesosPort")

  def Health : Future[Int] = {
    mesosUri.get().map {
      case response => response.status
    }
  }
}
