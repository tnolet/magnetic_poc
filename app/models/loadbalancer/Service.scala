package models.loadbalancer

import play.api.libs.json.Json

/**
 * Created by tim on 16/10/14.
 */
case class Service ( name: String, bindPort: Int, endPoint: String, mode: String )

object Service {

  implicit val srvReads = Json.reads[Service]
  implicit val svrWrites = Json.writes[Service]

}