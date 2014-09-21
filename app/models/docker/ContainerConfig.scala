package models.docker

import java.sql.Timestamp

import play.api.db.slick.Config.driver.simple._
import play.api.libs.json._
import scala.slick.lifted.Tag
import play.api.libs.functional.syntax._


/**
 * ContainerLbConfig stores the load balancer configuration for a deployed container
 */


case class ContainerConfig(id: Option[Long],
                           host: String,
                           ports: String,
                           weight: Long,
                           containerId: Long,
                           created_at: java.sql.Timestamp)

class ContainerConfigs(tag: Tag) extends Table[ContainerConfig](tag, "CONTAINER_CONFIG") {

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def host = column[String]("host", O.NotNull, O.Default("127.0.0.1"))
  def ports = column[String]("ports", O.NotNull, O.Default("0"))
  def weight = column[Long]("weight", O.Default(0))
  def containerId = column[Long]("containerId")
  def created_at = column[java.sql.Timestamp]("created_at", O.NotNull)
  def container = foreignKey("CONTAINER_FK", containerId, DockerContainers.containers)(_.id)

  def * = {
    (id.?, host, ports, weight, containerId, created_at) <>(ContainerConfig.tupled, ContainerConfig.unapply)
  }
}

object ContainerConfigs {

  val configs = TableQuery[ContainerConfigs]

  /**
   * Insert a new config
   * @param config a new config from the [[ContainerConfig]] type
   */
  def insert(config: ContainerConfig)(implicit s: Session) : Long  = {
    (configs returning configs.map(_.id)).insert(config)
  }

  def findByContainerId(id: Long)(implicit s: Session) = {
    configs.filter(_.containerId === id).firstOption
  }

  /**
   * Update a config
   * @param config the config to update
   */
  def update(config: ContainerConfig)(implicit s: Session) {
    configs.filter(_.id === config.id).update(config)
  }

  /**
   * Update the weight in a config
   * @param id the id of the config to update
   * @param weight the weight of the container in the load balancer configuration
   */
  def updateWeight(id: Long, weight: Long)(implicit s: Session) {
    configs.filter(_.id === id)
      .map(config => config.weight)
      .update(weight)
  }

  /**
   * Update the host in a config
   * @param id the id of the config to update
   * @param host the host of the container
   */
  def updateHost(id: Long, host: String)(implicit s: Session) {
    configs.filter(_.id === id)
      .map(config => (config.host))
      .update(host)
  }

  /**
   * Update the port in a config
   * @param id the id of the config to update
   * @param port the port of the container
   */
  def updatePort(id: Long, port: String)(implicit s: Session) {
    configs.filter(_.id === id)
      .map(config => (config.ports))
      .update(port)
  }




  /**
   * Update the weight in a config by container id
   * @param id the id of the container the config belongs to
   * @param weight the weight of the container in the load balancer configuration
   */
  def updateWeightByContainerId(id: Long, weight: Long)(implicit s: Session) {
    configs.filter(_.id === id)
      .map(config => (config.weight))
      .update(weight)
  }
}

object ContainerConfigJson {
  // Json reading/writing
  implicit val configWrites = Json.writes[ContainerConfig]

  implicit val configReads = (
    (__ \ 'id).read[Option[Long]] and
      (__ \ 'host).read[String] and
      (__ \ 'ports).read[String] and
      (__ \ 'weight).read[Long] and
      (__ \ 'containerId).read[Long] and
      (__ \ 'created_at).read[Long].map{ long => new Timestamp(long) }
    )(ContainerConfig)
}