package models.service

import models.Environments
import play.api.db.slick.Config.driver.simple._
import play.api.libs.json._
import scala.slick.lifted.Tag
import play.api.libs.functional.syntax._


case class Service(id: Option[Long],
                   port: Int,
                   state: String,
                   vrn: String,
                   environmentId: Long,
                   serviceTypeId: Long)

case class ServiceCreate(port: Int, environmentId: Long, serviceTypeId: Long)

class Services(tag: Tag) extends Table[Service](tag, "ServiceInstanceS") {

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def port = column[Int]("port", O.NotNull)
  def state = column[String]("state", O.NotNull)
  def vrn = column[String]("vrn", O.NotNull)
  def environmentId = column[Long]("environmentId")
  def serviceTypeId = column[Long]("serviceTypeId")

  def environment = foreignKey("ENV_FK", environmentId, Environments.environments)(_.id)
  def service = foreignKey("SERVICE_FK", serviceTypeId, ServiceTypes.serviceTypes)(_.id)

  def * = (id.?, port,   state, vrn, environmentId, serviceTypeId)  <> (Service.tupled, Service.unapply _)

}

object Services {

  val services = TableQuery[Services]

  def all(implicit s: Session): List[Service] = services.list

  /**
   * count returns the amount of Services
   */
  def count(implicit s: Session): Int =
    Query(services.length).first
  /**
   * Insert a new Service
   * @param service new Service
   */
  def insert(service: Service)(implicit s: Session) : Long = {
    (services returning services.map(_.id)).insert(service)
  }

  /**
   * Retrieve a Service based on its id
   * @param id unique id for this Service
   */
  def findById(id: Long)(implicit s: Session) =
    services.filter(_.id === id).firstOption

  /**
   * Retrieve services based the service type it is associated with
   * @param id unique id for the [[ServiceType]]
   */
  def findByServiceTypeId(id: Long)(implicit s: Session) =
    services.filter(_.serviceTypeId === id).list

  /**
   * Retrieve services based on the environment it is associated with
   * @param id unique id for the [[models.Environment]]
   */
  def findByEnvironmentId(id: Long)(implicit s: Session) =
    services.filter(_.environmentId === id).list

  /**
   * Retrieve service based on the vrn it is associated with
   * @param vrn unique vrn for this service
   */
  def findByVrn(vrn: String)(implicit s: Session) =
    services.filter(_.vrn === vrn).firstOption

  /**
   * Update a service by vrn
   * @param vrn the service to update
   * @param state the state of the service
   */
  def updateStateByVrn(vrn: String, state: String)(implicit s: Session) {
    services.filter(_.vrn === vrn)
      .map(s => s.state)
      .update(state)
  }
}

object ServiceJson {

  // Json reading/writing of basic Service case class
  implicit val ServiceWrites = Json.writes[Service]
  implicit val ServiceReads = Json.reads[Service]

  // Json reading/writing of ServiceCreate case class. Used for posting new service to the REST api
  implicit val ServiceWritesforCreate = Json.writes[ServiceCreate]
  implicit val serviceReadsforCreate = (
      (__ \ 'port).read[Int] and
      (__ \ 'environmentId).read[Long] and
      (__ \ 'serviceTypeId).read[Long]
    )(ServiceCreate)


}
