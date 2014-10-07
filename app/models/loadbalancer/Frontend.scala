package models.loadbalancer

import play.api.libs.json.Json

case class Frontend (name: String,
                     mode: String,
                     bindPort: Int,
                     bindIp: String,
                     useBackend: String,
                     options: Map[String,Boolean])

object Frontend {

  implicit val feReads = Json.reads[Frontend]
  implicit val feWrites = Json.writes[Frontend]

}