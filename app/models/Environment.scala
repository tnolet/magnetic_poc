package models

import play.api.db.slick.Config.driver.simple._
import play.api.libs.json._
import scala.slick.lifted.Tag
import lib.util.date.TimeStamp
import play.api.libs.functional.syntax._
import models.docker.DockerContainers

case class Environment(id: Option[Long],
                       name: String,
                       state: String)

class Environments(tag: Tag) extends Table[Environment](tag, "ENVIRONMENT") {

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.NotNull)
  def state = column[String]("state")
  def * = (id.?, name, state) <> (Environment.tupled, Environment.unapply _)

}

object Environments {

  val environments = TableQuery[Environments]

  def all(implicit s: Session): List[Environment] = environments.list

  def insert(env: Environment)(implicit s: Session) : Long  = {
    (environments returning environments.map(_.id)).insert(env)
  }

  def findById(id: Long)(implicit s: Session) =
    environments.filter(_.id === id).firstOption

  def update_state(id: Long, status: String)(implicit s: Session) {

    environments.filter(_.id === id)
      .map(env => (env.state))
      .update(status)
  }

  /**
   * count returns the amount of environments
   */
  def count(implicit s: Session): Int =
    Query(environments.length).first
}
