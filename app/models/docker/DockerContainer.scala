package models.docker

import java.sql.Timestamp

import models.Environments
import play.api.db.slick.Config.driver.simple._
import play.api.libs.json._
import scala.slick.lifted.Tag
import play.api.libs.functional.syntax._



case class DockerContainer(id: Option[Long],
                           vrn: String,
                           status: String,
                           imageRepo: String,
                           imageVersion: String,
                           ports: String,
                           environmentId: Long,
                           created_at: java.sql.Timestamp)

class DockerContainers(tag: Tag) extends Table[DockerContainer](tag, "DOCKER_CONTAINER") {

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def vrn = column[String]("vrn", O.NotNull)
  def status = column[String]("status", O.NotNull)
  def imageRepo = column[String]("imageRepo", O.NotNull)
  def imageVersion = column[String]("imageVersion", O.NotNull)
  def ports = column[String]("ports")
  def environmentId = column[Long]("environmentId")
  def created_at = column[java.sql.Timestamp]("created_at", O.NotNull)
  def environment = foreignKey("ENVIRONMENT_FK", environmentId, Environments.environments)(_.id)


  def * = (id.?, vrn, status, imageRepo, imageVersion, ports, environmentId, created_at) <>(DockerContainer.tupled, DockerContainer.unapply _)
}

object DockerContainers {

  // Json reading/writing
  implicit val containerWrites = Json.writes[DockerContainer]

  implicit val containerReads = (
    (__ \ 'id).read[Option[Long]] and
      (__ \ 'vrn).read[String] and
      (__ \ 'status).read[String] and
      (__ \ 'imageRepo).read[String] and
      (__ \ 'imageVersion).read[String] and
      (__ \ 'ports).read[String] and
      (__ \ 'environmentId).read[Long] and
      (__ \ 'created_at).read[Long].map{ long => new Timestamp(long) }
    )(DockerContainer)

  val containers = TableQuery[DockerContainers]

  def all(implicit s: Session): List[DockerContainer] = containers.list

  /**
   * Retrieve a container from the id
   * @param id the containers's id
   */
  def findById(id: Long)(implicit s: Session) =
    containers.filter(_.id === id).firstOption

  /**
   * Retrieve a container from the id
   * @param id
   */
  def findNonDestroyedById(id: Long)(implicit s: Session) =
    containers
      .filter(_.id === id)
      .firstOption

  /**
   * Insert a new container
   * @param container a new container from the DockerContainer type
   */
  def insert(container: DockerContainer)(implicit s: Session) : Long  = {
    (containers returning containers.map(_.id)).insert(container)
  }


  /**
   * Update a container
   * @param container the container to update
   */
  def update(container: DockerContainer)(implicit s: Session) {
    containers.filter(_.id === container.id).update(container)

  }

  /**
   * Update a container by vrn
   * @param vrn the container to update
   * @param status the state of the container
   */
  def updateStatusByVrn(vrn: String, status: String)(implicit s: Session) {
    containers.filter(_.vrn === vrn)
      .map(c => (c.status))
      .update(status)
  }


  /**
   * Count all containers
   */
  def count(implicit s: Session): Int =
    Query(containers.length).first
}