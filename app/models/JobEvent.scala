package models

import lib.util.date.TimeStamp
import play.api.db.slick.Config.driver.simple._
import scala.slick.lifted.Tag

case class JobEvent(id: Option[Long],
               status: String,
               eventType: String,
               jobId: Long,
               timestamp: java.sql.Timestamp)

class JobEvents(tag: Tag) extends Table[JobEvent](tag, "JOB_EVENTS") {

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def status = column[String]("status", O.NotNull)
  def eventType = column[String]("payload", O.NotNull)
  def jobId = column[Long]("jobId")
  def timestamp = column[java.sql.Timestamp]("timestamp")
  def job = foreignKey("JOB_FK", jobId, Jobs.jobs)(_.id)


  def * = (id.?, status, eventType , jobId, timestamp)  <> (JobEvent.tupled, JobEvent.unapply _)

}

object JobEvents {

  val events = TableQuery[JobEvents]

  def all(implicit s: Session): List[JobEvent] = events.list

  /**
   * Insert a new JobEvent
   * @param jobEvent a new jobEvent
   */
  def insert(jobEvent: JobEvent)(implicit s: Session) {
    events.insert(jobEvent)
  }


  /**
   * Retrieve a job based on its id
   * @param id unique id for this job
   */
  def findById(id: Long)(implicit s: Session) =
    events.filter(_.id === id).firstOption

}

