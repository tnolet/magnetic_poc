package lib.vamp.pulse

import com.typesafe.config.ConfigFactory
import play.api.libs.ws.WS
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * ScoreBoard functions as a proxy to the score board hosted on the Vamp Pulse Service
 */

object ScoreBoard {

  import play.api.Play.current

  val conf = ConfigFactory.load()
  val host = conf.getString("vamp.pulse.host")
  val port = conf.getInt("vamp.pulse.port")

  val api = s"http://$host:$port"

  def getScoreBoard: Future[Option[Map[String,Long]]] =

  WS.url(s"$api/scoreboard").get().map {

      case response =>
        response.json
          .validate[ Map[String,Long]].asOpt
    }

}
