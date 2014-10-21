package models.kairosdb

import play.api.libs.json.Json


case class DataPoint( name: String, timestamp: Long, value: Int, tags: Option[Map[String, String]])

object DataPointJson {

  // Json reading/writing
  implicit val datapointReads = Json.reads[DataPoint]
  implicit val datapointWrites = Json.writes[DataPoint]

}