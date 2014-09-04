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
                           maxconn: Option[Double],
                           check: Option[Boolean],
                           checkInterval: Option[Double]
                           )

object BackendServer {

  implicit val beServerReads = Json.reads[BackendServer]
  implicit val beServerWrites = Json.writes[BackendServer]

}