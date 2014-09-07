package models.loadbalancer

import play.api.libs.json.Json

/**
 * Created by tim on 04/09/14.
 */
case class Backend ( name: String,
                     servers: List[BackendServer],
                     options: Map[String,Boolean])

object Backend {

  implicit val beReads = Json.reads[Backend]
  implicit val beWrites = Json.writes[Backend]

}