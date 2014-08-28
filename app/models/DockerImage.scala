package models

import play.api.db.slick.Config.driver.simple._
import scala.slick.lifted.Tag


case class DockerImage(id: Option[Long], name: String, version: String)

class DockerImages(tag: Tag) extends Table[DockerImage](tag, "DOCKER_IMAGE") {

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.NotNull)
  def version = column[String]("version", O.NotNull)

  def * = (id.?, name, version) <>(DockerImage.tupled, DockerImage.unapply _)
}

object DockerImages {

  val images = TableQuery[DockerImages]

  def all(implicit s: Session): List[DockerImage] = images.list

  /**
   * Retrieve an image from the id
   * @param id unique id for this image
   */
  def findById(id: Long)(implicit s: Session) =
    images.filter(_.id === id).firstOption

  /**
   * Count all images
   */
  def count(implicit s: Session): Int =
    Query(images.length).first

  /**
   * Insert a new images
   * @param image a new image from the DockerImage type
   */
  def insert(image: DockerImage)(implicit s: Session) {
    images.insert(image)
  }

}