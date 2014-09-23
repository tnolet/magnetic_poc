package models.docker

import java.sql.Timestamp

import play.api.db.slick.Config.driver.simple._
import play.api.libs.json._
import scala.slick.lifted.Tag
import play.api.libs.functional.syntax._

/**
 * ContainerIsntance stores configuration for a deployed container instance
 */

case class ContainerInstance(id: Option[Long],
                           host: String,
                           ports: String,
                           weight: Int,
                           mesosId: String,
                           containerId: Long,
                           created_at: java.sql.Timestamp)

case class ContainerInstanceResult(id: Option[Long],
                           host: String,
                           ports: String,
                           weight: Long)


class ContainerInstances(tag: Tag) extends Table[ContainerInstance](tag, "CONTAINER_INSTANCE") {

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def host = column[String]("host", O.NotNull, O.Default("127.0.0.1"))
  def ports = column[String]("ports", O.NotNull, O.Default("0"))
  def weight = column[Int]("weight", O.Default(0))
  def mesosId = column[String]("mesosId")
  def containerId = column[Long]("containerId")
  def created_at = column[java.sql.Timestamp]("created_at", O.NotNull)
  def container = foreignKey("CONTAINER_FK", containerId, DockerContainers.containers)(_.id)

  def * = {
    (id.?, host, ports, weight, mesosId, containerId, created_at) <>(ContainerInstance.tupled, ContainerInstance.unapply)
  }
}

object ContainerInstances {

  val instances = TableQuery[ContainerInstances]

  /**
   * Insert a new config
   * @param config a new config from the [[ContainerInstance]] type
   */
  def insert(config: ContainerInstance)(implicit s: Session) : Long  = {
    (instances returning instances.map(_.id)).insert(config)
  }

  def findByContainerId(id: Long)(implicit s: Session) : Option[ContainerInstance] = {
    instances.filter(_.containerId === id).firstOption
  }

  /**
   * Update a config
   * @param config the config to update
   */
  def update(config: ContainerInstance)(implicit s: Session) {
    instances.filter(_.id === config.id).update(config)
  }

  /**
   * Update the weight in a config
   * @param id the id of the config to update
   * @param weight the weight of the container in the load balancer configuration
   */
  def updateWeight(id: Long, weight: Int)(implicit s: Session) {
    instances.filter(_.id === id)
      .map(config => config.weight)
      .update(weight)
  }

  /**
   * Update the host in a config
   * @param id the id of the config to update
   * @param host the host of the container
   */
  def updateHost(id: Long, host: String)(implicit s: Session) {
    instances.filter(_.id === id)
      .map(config => (config.host))
      .update(host)
  }

  /**
   * Update the port in a config
   * @param id the id of the config to update
   * @param port the port of the container
   */
  def updatePort(id: Long, port: String)(implicit s: Session) {
    instances.filter(_.id === id)
      .map(config => (config.ports))
      .update(port)
  }

  /**
   * Update the weight in a config by container id
   * @param id the id of the container the config belongs to
   * @param weight the weight of the container in the load balancer configuration
   */
  def updateWeightByContainerId(id: Long, weight: Int)(implicit s: Session) {
    instances.filter(_.id === id)
      .map(config => (config.weight))
      .update(weight)
  }
}

object ContainerInstanceJson {
  // Json reading/writing
  implicit val instanceWrites = Json.writes[ContainerInstance]

  implicit val instanceReads = (
    (__ \ 'id).read[Option[Long]] and
      (__ \ 'host).read[String] and
      (__ \ 'ports).read[String] and
      (__ \ 'weight).read[Int] and
      (__ \ 'mesosId).read[String] and
      (__ \ 'containerId).read[Long] and
      (__ \ 'created_at).read[Long].map{ long => new Timestamp(long) }
    )(ContainerInstance)
}