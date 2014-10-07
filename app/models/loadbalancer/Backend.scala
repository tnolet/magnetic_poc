package models.loadbalancer

import play.api.libs.json.Json

/**
 * Created by tim on 04/09/14.
 */
case class Backend ( name: String,
                     mode: String,
                     servers: List[BackendServer],
                     options: Map[String,Boolean]) {
  def addServer(server: BackendServer) : Backend = this.copy(
    servers = this.servers.::(server)
  )

}

object Backend {

  implicit val beReads = Json.reads[Backend]
  implicit val beWrites = Json.writes[Backend]

}