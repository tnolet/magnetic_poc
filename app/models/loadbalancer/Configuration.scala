package models.loadbalancer

import play.api.libs.json.Json

case class Configuration ( Frontends: List[Frontend], backends: List[Backend])

object Configuration {

  implicit val confReads = Json.reads[Configuration]
  implicit val confWrites = Json.writes[Configuration]

  /**
   * Add a backend to an existing configuration
   * @param conf represent a configuration object
   * @param backend represent a [[Backend]] object
   * @return a new [[Configuration]] object
   */
  def addBackend(conf: Configuration, backend: Backend): Configuration = {

    conf.copy(backends = conf.backends.::(backend))
  }
  /**
   * Remove a backend from an existing configuration by name
   * @param conf represent a configuration object
   * @param backendName represent the unique name of backend
   * @return a new [[Configuration]] object
   */

  def removeBackend(conf: Configuration, backendName: String): Configuration = {

    conf.copy(backends = conf.backends.filterNot( be => be.name == backendName))
  }
}