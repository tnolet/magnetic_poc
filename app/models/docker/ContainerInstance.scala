package models.docker

import java.sql.Timestamp

import play.api.db.slick.Config.driver.simple._
import play.api.libs.json._
import scala.slick.lifted.Tag
import play.api.libs.functional.syntax._

/**
 * ContainerInstance stores configuration for a deployed container instance.
 * A ContainerInstance directly maps to a "Task" in Mesos. Each [[DockerContainer]] has at least
 * one ContainerInstance, otherwise it isn't doing much.
 */

case class ContainerInstance(id: Option[Long],
                           vrn: String,
                           host: String,
                           ports: String,
                           weight: Int,
                           mesosId: String,
                           containerId: Long,
                           created_at: java.sql.Timestamp)

case class ContainerInstanceCreate(
                             vrn: String,
                             host: String,
                             ports: String,
                             weight: Int,
                             mesosId: String,
                             created_at: java.sql.Timestamp)

case class ContainerInstanceResult(id: Option[Long],
                           vrn: String,
                           host: String,
                           ports: String,
                           weight: Long)


class ContainerInstances(tag: Tag) extends Table[ContainerInstance](tag, "CONTAINER_INSTANCE") {

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def vrn = column[String]("vrn", O.NotNull)
  def host = column[String]("host", O.NotNull, O.Default("127.0.0.1"))
  def ports = column[String]("ports", O.NotNull, O.Default("0"))
  def weight = column[Int]("weight", O.Default(0))
  def mesosId = column[String]("mesosId")
  def containerId = column[Long]("containerId")
  def created_at = column[java.sql.Timestamp]("created_at", O.NotNull)
  def container = foreignKey("CONTAINER_FK", containerId, DockerContainers.containers)(_.id,onDelete=ForeignKeyAction.Cascade)

  def * = {
    (id.?, vrn, host, ports, weight, mesosId, containerId, created_at) <>(ContainerInstance.tupled, ContainerInstance.unapply)
  }
}

object ContainerInstances {

  val instances = TableQuery[ContainerInstances]

  /**
   * Insert a new config
   * @param instance a new instance from the [[ContainerInstance]] type
   */
  def insert(instance: ContainerInstance)(implicit s: Session) : Long  = {
    (instances returning instances.map(_.id)).insert(instance)
  }

  def findByContainerId(id: Long)(implicit s: Session) : List[ContainerInstance] = {
    instances.filter(_.containerId === id).list
  }


  /**
   * Update a config
   * @param config the config to update
   */
  def update(config: ContainerInstance)(implicit s: Session) {
    instances.filter(_.id === config.id).update(config)
  }

  /**
   * Update the weight of an instances
   * @param id the id of the instance to update
   * @param weight the weight of the container in the load balancer configuration
   */
  def updateWeight(id: Long, weight: Int)(implicit s: Session) {
    instances.filter(_.id === id)
      .map(config => config.weight)
      .update(weight)
  }

  /**
   * Update the host of an instance
   * @param id the id of the instance to update
   * @param host the host on which the instance runs
   */
  def updateHost(id: Long, host: String)(implicit s: Session) {
    instances.filter(_.id === id)
      .map(config => (config.host))
      .update(host)
  }

  /**
   * Update the port of an instance
   * @param id the id of the instance to update
   * @param port the port of the instance
   */
  def updatePort(id: Long, port: String)(implicit s: Session) {
    instances.filter(_.id === id)
      .map(config => (config.ports))
      .update(port)
  }

  /**
   * Update the weight in an instance by container id
   * @param id the id of the container the config belongs to
   * @param weight the weight of the container in the load balancer configuration
   */

  @deprecated
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
      (__ \ 'vrn).read[String] and
      (__ \ 'host).read[String] and
      (__ \ 'ports).read[String] and
      (__ \ 'weight).read[Int] and
      (__ \ 'mesosId).read[String] and
      (__ \ 'containerId).read[Long] and
      (__ \ 'created_at).read[Long].map{ long => new Timestamp(long) }
    )(ContainerInstance)
}