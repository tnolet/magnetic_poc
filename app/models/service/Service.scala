package models.service

import models.Environments
import models.docker.{ContainerInstances, DockerContainers, DockerContainerResult, DockerContainer}
import play.api.db.slick.Config.driver.simple._
import play.api.libs.json._
import scala.slick.lifted.Tag
import play.api.libs.functional.syntax._


case class Service(id: Option[Long],
                   port: Int,
                   mode: String,
                   state: String,
                   vrn: String,
                   environmentId: Long,
                   serviceTypeId: Long)

case class ServiceCreate(environmentId: Long, serviceTypeId: Long)

case class ServiceResult(id: Option[Long],
                         port: Int,
                         mode: String,
                         state : String,
                         serviceType: String,
                         environment: String,
                         version: String,
                         vrn: String,
                         serviceTypeId: Long,
                         containers: List[DockerContainerResult]
                          )

class Services(tag: Tag) extends Table[Service](tag, "SERVICES") {

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def port = column[Int]("port", O.NotNull)
  def mode = column[String]("mode", O.NotNull)
  def state = column[String]("state", O.NotNull)
  def vrn = column[String]("vrn", O.NotNull)
  def environmentId = column[Long]("environmentId")
  def serviceTypeId = column[Long]("serviceTypeId")

  def environment = foreignKey("ENV_FK", environmentId, Environments.environments)(_.id)
  def serviceType = foreignKey("SERVICE_TYPE_FK", serviceTypeId, ServiceTypes.serviceTypes)(_.id)

  def * = (id.?, port, mode, state, vrn, environmentId, serviceTypeId)  <> (Service.tupled, Service.unapply _)

}

object Services {

  val services = TableQuery[Services]

  def all(implicit s: Session): List[ServiceResult] = services.list.map ( srv =>

    findDetailsById(srv.id.get).get
  )

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
   * Retrieve a Service and its underpinning dependencies based on its id
   * @param id unique id for this Service
   */
  def findDetailsById(id: Long)(implicit s: Session) : Option[ServiceResult] = {
    services.filter(_.id === id).firstOption.map( srv =>  {

      val env = Environments.findById(srv.environmentId).get
      val srvType  = ServiceTypes.findById(srv.serviceTypeId).get
      val containers : List[DockerContainer] =  DockerContainers.findByServiceId(srv.id.get)

      val containersResult : List[DockerContainerResult] = containers.map (cnt => {
        val instances =  ContainerInstances.findByContainerId(cnt.id.get)
        DockerContainerResult.createResult(cnt, instances)
      })

       ServiceResult(srv.id,srv.port, srv.mode,srv.state,srvType.name, env.name, srvType.version, srv.vrn,srv.serviceTypeId,containersResult)

    })
  }

  /**
   * Retrieve services based the service type it is associated with
   * @param id unique id for the [[ServiceType]]
   */
  def findByServiceTypeId(id: Long)(implicit s: Session) : List[ServiceResult] =
    services.filter(_.serviceTypeId === id).list.map ( srv =>

      findDetailsById(srv.id.get).get
    )


  /**
   * Retrieve services based on the environment it is associated with
   * @param id unique id for the [[models.Environment]]
   */
  def findByEnvironmentId(id: Long)(implicit s: Session) =
    services.filter(_.environmentId === id).list.map ( srv =>

      findDetailsById(srv.id.get).get

    )


  /**
   * Retrieve service based on the vrn it is associated with
   * @param vrn unique vrn for this service
   */
  def findByVrn(vrn: String)(implicit s: Session) =
    services.filter(_.vrn === vrn).firstOption

  /**
   * Get a free port for a service based on the service type
   * @param id The service type id
   */
  def findFreePortByServiceType(id: Long)(implicit s: Session) : Int = {

    val basePort = ServiceTypes.findById(id).map(s => s.basePort).get


    val usedPorts = services
      .filter(_.serviceTypeId === id)
      .map(s => s.port)
      .list
      .sorted

    // no ports are in use, just use the base port
    if (usedPorts.isEmpty) {

      basePort

      // there is only one assigned port, and it equals the baseport
    } else if ( usedPorts.length == 1 && usedPorts(0) == basePort) {

      basePort + 1
    }

    else

    {

      // calculate the full possible range of ports
      val fullRange = List
        .range(basePort, usedPorts.max+1)
        .sorted

      // when all ports are nicely created and destroyed in a FIFO manner,
      // fullRange should be equal to usedPorts. This is not always the case of course,
      // but it can happen. Just choose the next number.

      if (fullRange == usedPorts) {

        usedPorts.max + 1

        // when services are deleted and created, gaps in the range occur.
        // We want to fill these gaps and always pick the lowest.

      } else {

        // deduct the already used ports and pick the lowest of the remainder
        val lowestFreePort = fullRange.filterNot(usedPorts.toSet).min

        lowestFreePort
      }
    }


  }


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


  /**
   * Delete a service
   * @param id the id of the image to delete
   */
  def delete(id: Long)(implicit s: Session) {
    services.filter(_.id === id).delete
  }
}

object ServiceJson {

  // Json reading/writing of basic Service case class
  implicit val ServiceWrites = Json.writes[Service]
  implicit val ServiceReads = Json.reads[Service]

  // Json reading/writing of ServiceCreate case class. Used for posting new service to the REST api
  implicit val ServiceWritesforCreate = Json.writes[ServiceCreate]
  implicit val ServiceReadsforCreate = (
      (__ \ 'environmentId).read[Long] and
      (__ \ 'serviceTypeId).read[Long]
    )(ServiceCreate)

  import models.docker.DockerContainerJson.containerResultWrites

  implicit val ServiceResultWrites = Json.writes[ServiceResult]

}
