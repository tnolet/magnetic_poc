package models

import java.util.Date
import play.api.db.slick.Config.driver.simple._
import scala.slick.lifted.Tag

case class DockerContainer(id: Option[Long], status: String, created: Date, ports: String)

class DockerContainers(tag: Tag) extends Table[DockerContainer](tag, "DOCKER_CONTAINER") {

  implicit val dateColumnType = MappedColumnType.base[Date, Long](d => d.getTime, d => new Date(d))

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.NotNull)
  def created = column[Date]("created", O.NotNull)
  def ports = column[String]("ports")

  def * = (id.?, name, created, ports) <>(DockerContainer.tupled, DockerContainer.unapply _)
}

object DockerContainers {

  val containers = TableQuery[DockerContainers]

  /**
   * Retrieve a container from the id
   * @param id
   */
  def findById(id: Long)(implicit s: Session) =
    containers.filter(_.id === id).firstOption

  /**
   * Count all containers
   */
  def count(implicit s: Session): Int =
    Query(containers.length).first
}