package models.loadbalancer

import play.api.libs.json.Json

case class Configuration ( Frontends: List[Frontend], backends: List[Backend])

object Configuration {

  implicit val confReads = Json.reads[Configuration]
  implicit val confWrites = Json.writes[Configuration]

  /**
   * Add a backend to an existing configuration
   * @param conf represents a configuration object
   * @param backend represent a [[Backend]] object
   * @return a new [[Configuration]] object
   */
  def addBackend(conf: Configuration, backend: Backend): Configuration = {

    conf.copy(backends = conf.backends.::(backend))
  }
  /**
   * Remove a backend from an existing configuration by vrn
   * @param conf represents a configuration object
   * @param vrn represents the vrn associated with the backend
   * @return a new [[Configuration]] object
   */

  def removeBackend(conf: Configuration, vrn: String): Configuration = {

    conf.copy(backends = conf.backends.filterNot( be => be.name == vrn))
  }

  /**
   * Add a frontend to an existing configuration
   * @param conf represents a configuration object
   * @param frontend represent a [[Frontend]] object
   * @return a new [[Configuration]] object
   */
  def addFrontend(conf: Configuration, frontend: Frontend): Configuration = {

    conf.copy(Frontends = conf.Frontends.::(frontend))
  }

  /**
   * Remove a frontend from an existing configuration by vrn
   * @param conf represents a configuration object
   * @param vrn represents the vrn associated with the frontend
   * @return a new [[Configuration]] object
   */

  def removeFrontend(conf: Configuration, vrn: String): Configuration = {

    conf.copy(Frontends = conf.Frontends.filterNot( fe => fe.name == vrn))
  }


}