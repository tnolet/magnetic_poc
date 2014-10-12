package models.docker

import java.sql.Timestamp

import models.service.Services
import play.api.db.slick.Config.driver.simple._
import play.api.libs.json._
import scala.slick.lifted.Tag
import play.api.libs.functional.syntax._


/**
 * DockerContainers represent a Docker container. The DockerContainer object is a holder for one ore
 * [[ContainerInstance]] object. DockerContainer maps to an "app" in Marathon, [[ContainerInstance]] maps
 * to Mesos Tasks.
 *
 */

case class DockerContainer(id: Option[Long],
                           vrn: String,
                           status: String,
                           imageRepo: String,
                           imageVersion: String,
                           masterWeight: Int,
                           instanceAmount: Long,
                           serviceId: Long,
                           created_at: java.sql.Timestamp)

case class DockerContainerResult(id: Option[Long],
                           vrn: String,
                           status: String,
                           imageRepo: String,
                           imageVersion: String,
                           masterWeight: Int,
                           instanceAmount: Long,
                           serviceId: Long,
                           instances: Seq[ContainerInstance],
                           created_at: java.sql.Timestamp)

case class Test(vrn: String, host: String)

class DockerContainers(tag: Tag) extends Table[DockerContainer](tag, "DOCKER_CONTAINER") {

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def vrn = column[String]("vrn", O.NotNull)
  def status = column[String]("status", O.NotNull)
  def imageRepo = column[String]("imageRepo", O.NotNull)
  def imageVersion = column[String]("imageVersion", O.NotNull)
  def masterWeight = column[Int]("masterWeight", O.Default(0))
  def instanceAmount = column[Long]("instanceAmount")
  def serviceId = column[Long]("serviceId")
  def created_at = column[java.sql.Timestamp]("created_at", O.NotNull)
  def service = foreignKey("SERVICE_FK", serviceId, Services.services)(_.id,onDelete=ForeignKeyAction.Cascade)


  def * = {
    (id.?, vrn, status, imageRepo, imageVersion, masterWeight, instanceAmount, serviceId, created_at) <>(DockerContainer.tupled, DockerContainer.unapply)
  }
}


object DockerContainerResult {

  def createResult(cnt: DockerContainer, instances: List[ContainerInstance]) : DockerContainerResult = {
    DockerContainerResult(cnt.id, cnt.vrn, cnt.status, cnt.imageRepo, cnt.imageVersion, cnt.masterWeight, cnt.instanceAmount, cnt.serviceId, instances, cnt.created_at)
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
   * Retrieve a container from the id
   * @param id the image's id
   */
  def findByImageId(id: Long)(implicit s: Session) =
    DockerImages.findById(id).map( image =>

      containers.filter(_.imageRepo === image.repo ).list

    )


  /**
   * Retrieve a container from the serviceId
   * @param id the service's id
   */
  def findByServiceId(id: Long)(implicit s: Session) =
    containers.filter(_.serviceId === id).list

  /**
   * Retrieve a container from the serviceId
   * @param vrn the service's vrn
   */
  def findByVrn(vrn: String)(implicit s: Session) =
    containers.filter(_.vrn === vrn).firstOption

  /**
   * Retrieve a list of instance by the container's VRN
   * @param vrn the containers vrn
   */

  def findInstancesByVrn(vrn: String)(implicit s: Session) : Option[List[ContainerInstance]] =
    containers
      .filter(_.vrn === vrn)
      .firstOption
      .map( cont => {
      ContainerInstances
        .findByContainerId(cont.id.get)
    })

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
   * Create a container instance
   * @param vrn the container to add the instance to
   * @param ci a container instance of the type [[ContainerInstanceCreate]]
   */
  def createInstanceByVrn(vrn: String, ci: ContainerInstanceCreate)(implicit s: Session): Unit = {
    containers.filter(_.vrn === vrn)
      .firstOption
      .map(cont => {
        val instance = ContainerInstance(Some(0), ci.vrn, ci.host, ci.ports, ci.weight, ci.mesosId, cont.id.get, ci.created_at)
        ContainerInstances.insert(instance)
    })
  }


  /**
   * Update a containers instance
   * @param vrn the container to update
   * @param host the host the container runs on
   * @param port the port the container runs on
   * @param mesosId the mesosId of the container. This maps to a mesos TaskId
   */
  def updateInstanceByVrn(vrn: String, host: String, port: String, mesosId: String)(implicit s: Session): Unit = {
    containers.filter(_.vrn === vrn)
      .firstOption
      .map(cont => {
        ContainerInstances.instances.filter(_.id === cont.id.get)
        .map( ins => (ins.host, ins.ports, ins.mesosId))
        .update(host, port, mesosId)
    })


  }

  def deleteInstanceByVrn(containerVrn: String, instanceVrn: String)(implicit s: Session): Unit = {
    containers.filter(_.vrn === containerVrn)
      .firstOption
      .map(cont => {
      ContainerInstances.instances.filter(_.vrn === instanceVrn)
        .delete

      })

  }

  /**
   * Update a container's masterWeight and the weight of its instances by vrn
   * @param vrn the container to update
   * @param weight the weight to set
   */
  def updateWeightByVrn(vrn: String, weight: Int)(implicit s: Session): Unit = {

    s.withTransaction{ containers.filter(_.vrn === vrn)
      .map(c => c.masterWeight)
      .update(weight)

      containers.filter(_.vrn === vrn)
      .firstOption
      .map(cnt => {
        val q = for { c <- ContainerInstances.instances if c.containerId === cnt.id.get } yield c.weight
        q.update(weight)

      }
    )}
  }

  /**
   * Count all containers
   */
  def count(implicit s: Session): Int =
    Query(containers.length).first

  /**
   * update the instance amount. Used only in create/delete container instance collections
   * @param id the containerId
   * @param amount the desired amount
   * @return
   */
  def setInstanceAmount(id: Long, amount: Long)(implicit s: Session) : Unit = {

    s.withTransaction {

      containers.filter(_.id === id)
        .map(c => c.instanceAmount)
        .update(amount)
    }
  }


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
      (__ \ 'masterWeight).read[Int] and
      (__ \ 'instanceAmount).read[Long] and
      (__ \ 'serviceId).read[Long] and
      (__ \ 'created_at).read[Long].map{ long => new Timestamp(long) }
    )(DockerContainer)

  import models.docker.ContainerInstanceJson.instanceWrites

  implicit val containerResultWrites = Json.writes[DockerContainerResult]


}