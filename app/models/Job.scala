package models

import java.sql.Timestamp

import play.api.db.slick.Config.driver.simple._
import play.api.libs.json._
import scala.slick.lifted.Tag
import lib.util.date.TimeStamp
import play.api.libs.functional.syntax._


case class Job(id: Option[Long],
               status: String,
               priority: Int,
               payload: String,
               queue: String,
               created_at: java.sql.Timestamp,
               updated_at: java.sql.Timestamp)

class Jobs(tag: Tag) extends Table[Job](tag, "JOB") {

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def status = column[String]("status", O.NotNull)
  def priority = column[Int]("priority", O.NotNull)
  def payload = column[String]("payload")
  def queue = column[String]("queue")
  def created_at = column[java.sql.Timestamp]("created_at")
  def updated_at = column[java.sql.Timestamp]("updated_at")

  def * = (id.?, status, priority ,payload, queue, created_at, updated_at)  <> (Job.tupled, Job.unapply _)

}


object Jobs {

  val jobs = TableQuery[Jobs]

  def all(implicit s: Session): List[Job] = jobs.list

  /**
   * Insert a new Job
   * @param job a new job the Job type
   */
  def insert(job: Job)(implicit s: Session) : Long  = {
    (jobs returning jobs.map(_.id)).insert(job)
  }

  /**
   * Update just the status field of an existing Job
   * @param id the id of the job to update
   */
  def update_status(id: Long, status: String)(implicit s: Session) {

    jobs.filter(_.id === id)
      .map(job => (job.status, job.updated_at))
      .update((status, TimeStamp.now))
  }

  /**
   * Retrieve a job based on its id
   * @param id unique id for this job
   */
  def findById(id: Long)(implicit s: Session) =
    jobs.filter(_.id === id).firstOption


  /**
   * Insert a job event for a job based on its id
   * @param id unique id for the job
   * @param event a [[JobEvent]]
   */
  def insertJobEvent(id: Long, event: JobEvent)(implicit s: Session) = {
    jobs.filter(_.id === id)
      .map( job => JobEvents.insert(event))
  }


  // Constants

  final val status = Map("new" -> "NEW", "active" -> "ACTIVE", "finished" -> "FINISHED", "failed" -> "FAILED")
  final val queue = Map(

    "deployment"          -> "DEPLOYMENT",
    "undeployment"        -> "UNDEPLOYMENT",
    "serviceDeployment"   -> "SERVICE_DEPLOYMENT",
    "serviceUnDeployment" -> "SERVICE_UNDEPLOYMENT",
    "scaling"             -> "SCALING"
  )

}

object JobJson {

  // Json reading/writing
  implicit val jobWrites = Json.writes[Job]

  implicit val jobReads = (
    (__ \ 'id).read[Option[Long]] and
      (__ \ 'status).read[String] and
      (__ \ 'priority).read[Int] and
      (__ \ 'payload).read[String] and
      (__ \ 'queue).read[String] and
      (__ \ 'created_at).read[Long].map{ long => new Timestamp(long) } and
      (__ \ 'updated_at).read[Long].map{ long => new Timestamp(long) }
    )(Job)

}

