package models

import play.api.db.slick.Config.driver.simple._
import scala.slick.lifted.Tag
import lib.util.date.TimeStamp


case class Job(id: Option[Long],
               status: String,
               priority: Int,
               payload: String,
               created_at: java.sql.Timestamp,
               updated_at: java.sql.Timestamp)

class Jobs(tag: Tag) extends Table[Job](tag, "JOB") {

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def status = column[String]("status", O.NotNull)
  def priority = column[Int]("priority", O.NotNull)
  def payload = column[String]("payload")
  def created_at = column[java.sql.Timestamp]("created_at")
  def updated_at = column[java.sql.Timestamp]("updated_at")

  def * = (id.?, status, priority ,payload, created_at, updated_at)  <> (Job.tupled, Job.unapply _)

}


object Jobs {

  val jobs = TableQuery[Jobs]

  def all(implicit s: Session): List[Job] = jobs.list

  /**
   * Insert a new Job
   * @param job a new image job the Job type
   */
  def insert(job: Job)(implicit s: Session) {
    jobs.insert(job)
  }


  /**
   * Update just the status field of an existing Job
   * @param id the id of the job to update
   */
  def update_status(id: Long, status: String)(implicit s: Session) {
//    val i = for { j <- jobs if j.id === id } yield j.status
//    i.update(status)

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

  }

