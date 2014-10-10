package models.service

import play.api.db.slick.Config.driver.simple._
import play.api.libs.json._
import scala.slick.lifted.Tag

case class ServiceType(id: Option[Long],
                    name: String,
                    version: String,
                    mode: String,    // "tcp" or "http"
                    basePort: Int,
                    systemService: Boolean = false
                        )

class ServiceTypes(tag: Tag) extends Table[ServiceType](tag, "SERVICE_TYPES") {

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.NotNull)
  def version = column[String]("version", O.NotNull)
  def mode = column[String]("mode", O.NotNull)
  def basePort = column[Int]("basePort", O.NotNull)
  def systemService = column[Boolean]("systemService", O.NotNull)

  def * = (id.?, name, version, mode, basePort, systemService)  <> (ServiceType.tupled, ServiceType.unapply _)

}

object ServiceTypes {

  val serviceTypes = TableQuery[ServiceTypes]

  def all(implicit s: Session): List[ServiceType] = serviceTypes.list

  /**
   * count returns the amount of services
   */
  def count(implicit s: Session): Int =
    Query(serviceTypes.length).first
  /**
   * Insert a new Service
   * @param Service a new Service
   */
  def insert(Service: ServiceType)(implicit s: Session) : Long = {
    (serviceTypes returning serviceTypes.map(_.id)).insert(Service)
  }

  /**
   * Retrieve a service based on its id
   * @param id unique id for this service
   */
  def findById(id: Long)(implicit s: Session) =
    serviceTypes.filter(_.id === id).firstOption
}

object ServiceTypeJson {

  // Json reading/writing
  implicit val ServiceTypeReads = Json.reads[ServiceType]
  implicit val ServiceTypeWrites = Json.writes[ServiceType]
}
