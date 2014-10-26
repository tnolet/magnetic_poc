package models.loadbalancer

import play.api.libs.json.Json

/**
 * Case classes use for the loadbalancer
 */
case class BackendServer (
                           name: String,
                           host: String,
                           port: Int,
                           weight: Int,
                           maxconn: Option[Int],
                           check: Option[Boolean],
                           checkInterval: Option[Int]
                           )

case class BackendServerCreate(
                            host: String,
                            port: Int,
                            vrn: String,
                            backend: String,
                            weight: Option[Int] = Some(0)
                           )



object BackendServer {

  implicit val beServerReads = Json.reads[BackendServer]
  implicit val beServerWrites = Json.writes[BackendServer]

}