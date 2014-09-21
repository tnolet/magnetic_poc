package models

import models.service.{ServiceResult, Services, Service}
import play.api.db.slick.Config.driver.simple._
import play.api.libs.json._
import scala.slick.lifted.Tag

case class Environment(id: Option[Long],
                       name: String,
                       state: String)

case class EnvironmentResult(id: Option[Long],
                             name: String,
                             state: String,
                              services: List[ServiceResult]
                              )

class Environments(tag: Tag) extends Table[Environment](tag, "ENVIRONMENT") {

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.NotNull)
  def state = column[String]("state")
  def * = (id.?, name, state) <> (Environment.tupled, Environment.unapply _)

}

object Environments {

  val environments = TableQuery[Environments]

  val environments_with_services = for {
    (e,d) <- environments leftJoin  Services.services on (_.id === _.environmentId)
  } yield (e.name, d.vrn.?)


  def all_with_containers(implicit s: Session) : Seq[(String,Option[String])] = environments_with_services.list

  def all(implicit s: Session): List[Environment] = environments.list

  def insert(env: Environment)(implicit s: Session) : Long  = {
    (environments returning environments.map(_.id)).insert(env)
  }

  def findById(id: Long)(implicit s: Session) = {
    environments.filter(_.id === id).firstOption
  }

  def findIdByName(name: String)(implicit s: Session) =
    environments.filter(_.name === name).firstOption

  def update_state(id: Long, status: String)(implicit s: Session) {

    environments.filter(_.id === id)
      .map(env => env.state)
      .update(status)
  }

  /**
   * count returns the amount of environments
   */
  def count(implicit s: Session): Int =
    Query(environments.length).first
}

object EnvironmentJson {

  import models.service.ServiceJson.ServiceResultWrites

  implicit val envReads = Json.reads[Environment]
  implicit val envWrites = Json.writes[Environment]
  implicit val envResultWrites = Json.writes[EnvironmentResult]
}

