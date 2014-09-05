package models.docker

import play.api.db.slick.Config.driver.simple._
import play.api.libs.json.Json

import scala.slick.lifted.Tag


case class DockerImage(id: Option[Long], name: String, repo: String,version: String, arguments: String)

class DockerImages(tag: Tag) extends Table[DockerImage](tag, "DOCKER_IMAGE") {

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.NotNull)
  def repo = column[String]("repo", O.NotNull)
  def version = column[String]("version", O.NotNull)
  def arguments = column[String]("arguments")

  def * = (id.?, name, repo, version, arguments) <> (DockerImage.tupled, DockerImage.unapply _)
}

object DockerImages {

  implicit val imageReads = Json.reads[DockerImage]
  implicit val imageWrites = Json.writes[DockerImage]

  val images = TableQuery[DockerImages]

  def all(implicit s: Session): List[DockerImage] = images.list

  /**
   * Retrieve an image based on its id
   * @param id unique id for this image
   */
  def findById(id: Long)(implicit s: Session) =
    images.filter(_.id === id).firstOption

  /**
   * Retrieve an image based on its name
   * @param name unique name for this image
   */
  def findByName(name: String)(implicit s: Session) =
    images.filter(_.name === name).firstOption


  /**
   * count returns the amount of images
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

  /**
   * Delete an image
   * @param id the id of the image to delete
   */
  def delete(id: Long)(implicit s: Session) {
    images.filter(_.id === id).delete
  }

}