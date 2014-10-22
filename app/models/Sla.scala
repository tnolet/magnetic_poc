package models

import play.api.libs.json._

/**
 * The Sla describes the performance criteria a service should stick to
 * @param metricType The type of metric to check for. Currently only Haproxy metrics like "qcur" or "rtime"
 * @param lowThreshold The lower threshold. Values below this should deescalate
 * @param highThreshold The high threshold. Values above this should trigger escalation
 * @param backOffTime The time in seconds to back off when escalating or deescalating.
 * @param backOffStages The amount of times we can back off the [[backOffTime]]. Together this prohibits flapping
 * @param maxEscalations The hard upper limit  of escalations that can be triggered.
 * @param vrn the unique id of the object the SLA belongs to
 */
case class Sla(id: Option[Long],
               metricType: String,
               lowThreshold: Long,
               highThreshold: Long,
               backOffTime: Int,
               backOffStages: Int,
               maxEscalations: Int,
               vrn: String,
               serviceId: Long)


object SlaJson {

  // Json reading/writing
  implicit val SlaWrites = Json.writes[Sla]

  implicit val SlaReads = Json.reads[Sla]
}

