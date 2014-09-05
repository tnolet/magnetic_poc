package lib.marathon

import com.typesafe.config.ConfigFactory
import models.docker.DockerImage
import models.marathon.Tasks
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
  val marathonApi = s"${marathonBaseUri}/v${marathonApiVersion}"

  def Ping : Future[Int] = {
    WS.url(marathonBaseUri + "/ping").get().map {
      case response => response.status
    }
  }

  def host = marathonHost
  def port = marathonPort
  def uri = marathonBaseUri

  /**
   * Submit a Docker image and all its meta-data to Marathon, but with 0 instances. This helps us check for errors and
   * delay the actual start up.
   * @param image a Docker image, represented by [[DockerImage]]
   */
  def submitContainer(image: DockerImage) : Future[Int] = {
    val data = Json.parse(s"""
                            {
                                "container": {
                                    "type": "DOCKER",
                                    "docker": {
                                        "image": "${image.repo}"
                                    }
                              },
                              "id": "${appId(image.name, image.version)}",
                              "instances": "0",
                              "cpus": "0.2",
                              "mem": "512",
                              "ports": [0],
                              "cmd": "${image.arguments}"
                              }
                    """)
     WS.url(s"$marathonApi/apps").post(data).map {
       case response => response.status
     }
  }

  /**
   * Submit a Docker image and all its meta-data to Marathon and start the staging process.
   * @param image a Docker image, represented by [[DockerImage]]
   */

  def stageContainer(image: DockerImage) : Future[Int] = {
    val id = appId(image.name, image.version)
    val data = Json.parse(s"""
                            {
                                "container": {
                                    "type": "DOCKER",
                                    "docker": {
                                        "image": "${image.repo}"
                                    }
                              },
                              "id": "$id",
                              "instances": "1",
                              "cpus": "0.2",
                              "mem": "512",
                              "ports": [0],
                              "cmd": "${image.arguments}"
                              }
                    """)
    WS.url(s"$marathonApi/apps/$id").put(data).map {
      case response => response.status
    }
  }

  def tasks(id: String): Future[JsValue] = {
    WS.url(s"$marathonApi/apps/$id/tasks").get().map {
      case response => {
        response.json
      }
    }
  }

  // Helper function to create application ID's based on repo names
  def appId(name: String, version: String) = name.replace("/","-").replace("_","-").concat("-" + version)
}
