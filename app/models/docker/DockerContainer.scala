package models.docker

import java.sql.Timestamp

import models.service.Services
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
                           serviceId: Long,
                           created_at: java.sql.Timestamp)

case class DockerContainerResult(id: Option[Long],
                           vrn: String,
                           status: String,
                           imageRepo: String,
                           imageVersion: String,
                           ports: String,
                           serviceId: Long,
                           instances: ContainerInstance,
                           created_at: java.sql.Timestamp)

case class Test(vrn: String, host: String)

class DockerContainers(tag: Tag) extends Table[DockerContainer](tag, "DOCKER_CONTAINER") {

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def vrn = column[String]("vrn", O.NotNull)
  def status = column[String]("status", O.NotNull)
  def imageRepo = column[String]("imageRepo", O.NotNull)
  def imageVersion = column[String]("imageVersion", O.NotNull)
  def ports = column[String]("ports")
  def serviceId = column[Long]("serviceId")
  def created_at = column[java.sql.Timestamp]("created_at", O.NotNull)
  def service = foreignKey("SERVICE_FK", serviceId, Services.services)(_.id)


  def * = {
    (id.?, vrn, status, imageRepo, imageVersion, ports, serviceId, created_at) <>(DockerContainer.tupled, DockerContainer.unapply)
  }
}

object DockerContainers {


  val containers = TableQuery[DockerContainers]

  def all(implicit s: Session): List[DockerContainer] = containers.list

  /**
   * Retrieve a container from the id
   * @param id the containers's id
   */
  def findById(id: Long)(implicit s: Session) =
    containers.filter(_.id === id).firstOption

  /**
   * Retrieve a container from the serviceId
   * @param id the service's id
   */
  def findByServiceId(id: Long)(implicit s: Session) =
    containers.filter(_.serviceId === id).list

  /**
   * Todo: finish this with correct filtering
   * Retrieve a container from the id
   * @param id the id of the container
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
      .map(c => c.status)
      .update(status)
  }

  /**
   * Update a container's host and port config by vrn
   * @param vrn the container to update
   * @param host the host the container runs on
   * @param port the port the container runs on
   */
  def updateConfigByVrn(vrn: String, host: String, port: String, mesosId: String)(implicit s: Session): Unit = {
    containers.filter(_.vrn === vrn)
      .firstOption
      .map(cont => {
      ContainerInstances.instances.filter(_.id === cont.id.get)
      .map( conf => (conf.host, conf.ports, conf.mesosId))
      .update(host, port, mesosId)
    })
  }


  /**
   * Update a container's weight  by vrn
   * @param vrn the container to update
   * @param weight the weight to set
   */
  def updateWeightByVrn(vrn: String, weight: Int)(implicit s: Session): Unit = {

     println("updating weight")

    containers.filter(_.vrn === vrn)
      .firstOption
      .map(cont => {
      ContainerInstances.instances.filter(_.id === cont.id.get)
        .map( conf => conf.weight)
        .update(weight)
    })
  }

  /**
   * Count all containers
   */
  def count(implicit s: Session): Int =
    Query(containers.length).first
}

object DockerContainerJson {
  // Json reading/writing
  implicit val containerWrites = Json.writes[DockerContainer]

  implicit val containerReads = (
    (__ \ 'id).read[Option[Long]] and
      (__ \ 'vrn).read[String] and
      (__ \ 'status).read[String] and
      (__ \ 'imageRepo).read[String] and
      (__ \ 'imageVersion).read[String] and
      (__ \ 'ports).read[String] and
      (__ \ 'serviceId).read[Long] and
      (__ \ 'created_at).read[Long].map{ long => new Timestamp(long) }
    )(DockerContainer)

  import models.docker.ContainerInstanceJson.instanceWrites

  implicit val containerResultWrites = Json.writes[DockerContainerResult]


}