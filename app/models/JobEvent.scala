package models

import java.sql.Timestamp
import play.api.db.slick.Config.driver.simple._
import play.api.libs.json._
import scala.slick.lifted.Tag
import play.api.libs.functional.syntax._


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
   * @param id unique id for this jobEvent
   */
  def findById(id: Long)(implicit s: Session) =
    events.filter(_.id === id).firstOption

  /**
   * Retrieve a job based on its foreign key, which is a job id
   * @param id unique id for the job
   */
  def findByJobId(id: Long)(implicit s: Session) =
    events.filter(_.jobId === id).list
}

object JobEventJson {

  // Json reading/writing
  implicit val jobEventWrites = Json.writes[JobEvent]

  implicit val jobEventReads = (
    (__ \ 'id).read[Option[Long]] and
      (__ \ 'status).read[String] and
      (__ \ 'eventType).read[String] and
      (__ \ 'jobId).read[Long] and
      (__ \ 'timestamp).read[Long].map{ long => new Timestamp(long) }
    )(JobEvent)

}
