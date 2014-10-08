package models.marathon

import play.api.libs.json._

/**
 * models an "app" definition in Marathon. Used for creating JSON message and submitting to Marathon
 */


case class Constraint(attribute: String, operator: String, value: String)

case class PortMapping(
                        containerPort: Int,
                        hostPort: Int = 0,
                        servicePort: Int = 9000,
                        protocol: String
                        )

case class Docker(
                   image: String = "",
                   network: Option[String] = None,
                   portMappings: List[PortMapping]  = List()
                   )

case class Volume(
                    containerPath: String,
                    hostPath: String,
                    mode: String
                    )

case class Container(
                      _type: String = "DOCKER",
                      docker: Docker,
                      volumes: List[Volume] = List()
                      )

case class UpgradeStrategy( minimumHealthCapacity: Double = 1)


case class HealthCheck(
                         protocol: String,
                         path: String,
                         gracePeriodSeconds: Double,
                         intervalSeconds: Double,
                         portIndex: Double,
                         timeoutSeconds: Double,
                         maxConsecutiveFailures: Double
                         )



case class MarathonApp(
                           id: String,
                           cmd: String = "",
                           args: List[String] = List(),
                           container: Container,
                           cpus: Double = 1,
                           mem: Double = 1024,
                           disk: Double = 0,
                           env: Option[List[Map[String,String]]] = None,
                           constraints: List[Constraint] = List(),
                           healthChecks: List[HealthCheck] = List(),
                           instances: Int = 1,
                           ports: List[Int] = List(0),
                           backoffSeconds: Double = 1,
                           backoffFactor: Double = 1.15,
                           uris: List[String] = List(),
                           dependencies: List[String] = List(),
                           upgradeStrategy: UpgradeStrategy = new UpgradeStrategy
                           )

object MarathonApp  {

  /**
   * Helper that builds a marathon app with almost only defaults
   * @param appId the id of the app. Should be a VRN
   * @param appImage the Docker image to deploy
   * @param instanceAmount number of instances
   * @return
   */
  def simpleAppBuilder(appId: String, appImage: String, instanceAmount: Int) : MarathonApp = {

    val container = Container( docker = Docker(image = appImage))
    MarathonApp( id = appId, container = container, instances = instanceAmount)

  }

  /**
   * Helper that builds a marathon app based on bridged Docker instances.
   * See: https://mesosphere.github.io/marathon/docs/native-docker.html
   * @param appId the id of the app. Should be a VRN
   * @param appImage the Docker image to deploy
   * @param instanceAmount number of instances
   * @return
   */
  def bridgedAppBuilder(appId: String, appImage: String, port: Int, instanceAmount: Int) : MarathonApp = {

    val portMap = PortMapping( containerPort = port, protocol = "tcp" )
    val docker = Docker( image = appImage, network = Some("BRIDGE"), portMappings = List(portMap))
    val container = Container( docker = docker)
    MarathonApp( id = appId, container = container, instances = instanceAmount)

  }
}

object MarathonAppJson {


      implicit val port = Json.writes[PortMapping]
      implicit val appDock = Json.writes[Docker]
      implicit val appVolume = Json.writes[Volume]

      // more verbose writer because we need to adjust the _type keyword
      implicit val locationWrites = new Writes[Container] {
        def writes(container: Container) = Json.obj(
          "type" -> container._type,
          "docker" -> container.docker,
          "volumes" -> container.volumes
        )
      }

  implicit val appCons = Json.writes[Constraint]
      implicit val appHealth = Json.writes[HealthCheck]
      implicit val appUpgrade = Json.writes[UpgradeStrategy]


      implicit val MarathonAppWrites = Json.writes[MarathonApp]
}

