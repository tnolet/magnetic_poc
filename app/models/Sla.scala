package models

import java.sql.Timestamp

import models.service.Services
import play.api.db.slick.Config.driver.simple._
import play.api.libs.json._
import scala.slick.lifted.Tag
import play.api.libs.functional.syntax._
import lib.util.date.TimeStamp

/**
 * The Sla describes the performance criteria a service should stick to
 * @param metricType The type of metric to check for. Currently only Haproxy metrics like "backend.rtime" or "frontend.scur"
 * @param lowThreshold The lower threshold. Values below this should deescalate
 * @param highThreshold The high threshold. Values above this should trigger escalation
 * @param backOffTime The time in seconds to back off when escalating or deescalating.
 * @param backOffStages The amount of times we can back off the [[backOffTime]]. Together this prohibits flapping
 * @param escalations The currently used amount of escalations
 * @param maxEscalations The hard upper limit  of escalations that can be triggered.
 * @param vrn the unique id of the object the SLA belongs to, i.e. "vrn-development-service-e47bdfe2""
 */
case class Sla(id: Option[Long],
               state: String,
               metricType: String,
               lowThreshold: Long,
               highThreshold: Long,
               backOffTime: Int,
               backOffStages: Int,
               currentStage: Int,
               escalations: Int = 0,
               maxEscalations: Int,
               vrn: String,
               serviceId: Long,
               created_at: java.sql.Timestamp,
               updated_at: java.sql.Timestamp
                )

case class SlaCreate(
               metricType: String,
               lowThreshold: Long,
               highThreshold: Long,
               backOffTime: Int,
               backOffStages: Int,
               maxEscalations: Int,
               vrn: String,
               serviceId: Long
                )

// todo: use enums for all states
object SlaState extends Enumeration {
  type State = Value
  val NEW,ACTIVE,OK,WARNING,ESCALATED,FAILED,SUSPENDED,DESTROYED = Value
}

class Slas(tag: Tag) extends Table[Sla](tag, "SLAS") {

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def state = column[String]("state", O.NotNull)
  def metricType = column[String]("metricType", O.NotNull)
  def lowThreshold = column[Long]("lowThreshold", O.NotNull)
  def highThreshold = column[Long]("highThreshold", O.NotNull)
  def backoffTime = column[Int]("backoffTime", O.NotNull)
  def backoffStages = column[Int]("backoffStages", O.NotNull)
  def currentStage = column[Int]("currentStage", O.NotNull)
  def escalations = column[Int]("wscalations", O.NotNull)
  def maxEscalations = column[Int]("maxEscalations", O.NotNull)
  def vrn = column[String]("vrn", O.NotNull)
  def serviceId = column[Long]("serviceId", O.NotNull)
  def created_at = column[java.sql.Timestamp]("created_at")
  def updated_at = column[java.sql.Timestamp]("updated_at")

  def service = foreignKey("SERVICE_SLA_FK", serviceId, Services.services)(_.id,onDelete=ForeignKeyAction.Cascade)

  def * = (id.?, state, metricType, lowThreshold, highThreshold, backoffTime, backoffStages, currentStage, escalations, maxEscalations, vrn, serviceId, created_at, updated_at)  <> (Sla.tupled, Sla.unapply _)

}

object Slas {

  val slas = TableQuery[Slas]

  def all(implicit s: Session): List[Sla] = slas.list

  /**
   * Insert a new Sla
   * @param sla a new [[Sla]]
   */
  def insert(sla: Sla)(implicit s: Session) : Long = {
    (slas returning slas.map(_.id)).insert(sla)
  }

  /**
   * Retrieve a sla based on its id
   * @param id unique id for this Sla
   */
  def findById(id: Long)(implicit s: Session) =
    slas.filter(_.id === id).firstOption

  /**
   * Retrieve a sla based on its foreign key, which is a service id
   * @param id unique id for the job
   */
  def findByServiceId(id: Long)(implicit s: Session) =
    slas.filter(_.serviceId === id).list

  /**
   * Update just the state field of an existing Sla
   * @param id the id of the Sla to update
   */
  def update_state(id: Long, state: SlaState.State)(implicit s: Session) {

    slas.filter(_.id === id)
      .map(sla => (sla.state, sla.updated_at))
      .update((state.toString, TimeStamp.now))
  }

  /**
   * Update just the state field of an existing Sla
   * @param vrn the vrn of the Sla to update
   */
  def update_state_by_vrn(vrn: String, state: SlaState.State, currentStage: Int, escalations: Int)(implicit s: Session) {

    slas.filter(_.vrn === vrn)
      .map(sla => (sla.state, sla.currentStage, sla.escalations, sla.updated_at))
      .update((state.toString,currentStage, escalations, TimeStamp.now))
  }


  /**
   * Delete a sla
   * @param id the id of the sla to delete
   */
  def delete(id: Long)(implicit s: Session) {
    slas.filter(_.id === id).delete
  }

  /**
   * Mark an SLA for deletion
   * @param id the id of the sla to delete. Deletion actually just sets the state to [[models.SlaState.DESTROYED]]
   */
  def mark_for_deletion(id: Long)(implicit s: Session) {
    slas.filter(_.id === id)
      .map(sla => sla.state)
      .update(SlaState.DESTROYED.toString)
  }


}


object SlaJson {

  // Json reading/writing
  implicit val SlaWrites = Json.writes[Sla]
  implicit val SlaReads = (
    (__ \ 'id).read[Option[Long]] and
      (__ \ 'state).read[String] and
      (__ \ 'metricType).read[String] and
      (__ \ 'lowThreshold).read[Long] and
      (__ \ 'highThreshold).read[Long] and
      (__ \ 'backoffTime).read[Int] and
      (__ \ 'backoffStages).read[Int] and
      (__ \ 'currentStage).read[Int] and
      (__ \ 'escalations).read[Int] and
      (__ \ 'maxEscalations).read[Int] and
      (__ \ 'vrn).read[String] and
      (__ \ 'serviceId).read[Long] and
      (__ \ 'created_at).read[Long].map{ long => new Timestamp(long)} and
      (__ \ 'updated_at).read[Long].map{ long => new Timestamp(long)}
    )(Sla)

  implicit val SlaCreateWrites = Json.writes[SlaCreate]
  implicit val SlaCreateReads = Json.reads[SlaCreate]

}

