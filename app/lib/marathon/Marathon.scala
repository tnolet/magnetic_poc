package lib.marathon

import com.typesafe.config.ConfigFactory
import models.docker.DockerImage
import models.marathon.{MarathonApp, Docker, MarathonAppJson}
import play.api.libs.ws.WS
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json._


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
  val marathonApi = s"$marathonBaseUri/v$marathonApiVersion"

  def host = marathonHost
  def port = marathonPort
  def uri = marathonBaseUri


  def Health = Ping

  def Ping : Future[Int] = {
    WS.url(marathonBaseUri + "/ping").get().map {
      case response => response.status
    }
  }


  /**
   * Submit a Docker image and all its meta-data to Marathon, but with 0 instances. This helps us check for errors and
   * delay the actual start up.
   * @param vrn an unique name for this resource based on [[lib.util.vamp.Naming.createVrn()]]
   * @param image a Docker image, represented by [[DockerImage]]
   */
  def submitContainer(vrn: String, image: DockerImage) : Future[Int] = {

    import MarathonAppJson.MarathonAppWrites


    val app = Json.toJson(MarathonApp.simpleAppBuilder(vrn, image.repo,0))
    println(Json.stringify(app))

     WS.url(s"$marathonApi/apps").post(app).map {
       case response => response.status
     }
  }

  /**
   * Submit a Docker image and all its meta-data to Marathon and start the staging process.
   * @param vrn a unique name for this resource based on [[lib.util.vamp.Naming.createVrn()]]
   * @param image a Docker image, represented by [[DockerImage]]
   */
  def stageContainer(vrn: String, image: DockerImage) : Future[Int] = {

    import MarathonAppJson.MarathonAppWrites

    val app = Json.toJson(MarathonApp.simpleAppBuilder(vrn, image.repo,1))

    WS.url(s"$marathonApi/apps/$vrn").put(app).map {
      case response => response.status
    }
  }

  /**
   * Submits a scaling request to Marathon for an existing container
   * @param vrn unique name for the resource based on [[lib.util.vamp.Naming.createVrn()]] which references the container
   * @param amount the scale amount. This translates to Mesos tasks assigned to Marathon apps
   * @return a Future for return code
   */
  def scaleContainer(vrn: String, amount: Long) : Future[Int] = {


    val data = Json.parse(s"""{
                            "instances" : ${amount.toInt}
                          }"""

    )

    WS.url(s"$marathonApi/apps/$vrn").put(data).map {
      case response => response.status
    }

  }

  /**
   * Destroy a container running on Marathon.
   * @param vrn a unique name for this resource based on [[lib.util.vamp.Naming.createVrn()]]
   */
  def destroyContainer(vrn: String) : Future[Int] = {

    WS.url(s"$marathonApi/apps/$vrn").delete().map {
      case response => response.status
    }

  }

  def tasks(vrn: String): Future[JsValue] = {
    WS.url(s"$marathonApi/apps/$vrn/tasks").get().map {
      case response => response.json
    }
  }

  def app(vrn: String): Future[JsValue] = {
    WS.url(s"$marathonApi/apps/$vrn").get().map {
      case response => response.json
    }
  }
}
