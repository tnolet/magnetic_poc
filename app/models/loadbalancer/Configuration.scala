package models.loadbalancer

import play.api.libs.json.Json

case class Configuration ( Frontends: List[Frontend],
                           backends: List[Backend],
                           services: Option[List[Service]] = None)

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

  /**
   * Add a backend server to an existing backend by vrn of the backend
   * @param conf represents a configuration object
   * @param backendVrn represents the vrn associated with the backend
   * @param server an object of the [[BackendServer]] type. This the server to attach to the backend
   * @return a new [[Configuration]] object
   */
  def addServerToBackend(conf: Configuration, backendVrn: String, server: BackendServer): Configuration = {

    // get the correct backend based on the name/vrn
    val backendToEdit : Backend = conf.backends.filter( be => be.name == backendVrn).head

    // delete it from the configuration so we can replace it
    val newConf = conf.copy(backends = conf.backends.filter( be => be.name != backendVrn))

    // add the server
    val newBackend = backendToEdit.addServer(server)

    // add the backend with the new server to the config
    conf.copy(backends = newConf.backends.::(newBackend))

  }

  /**
   * Remove a backend server from an existing backend by vrn of the server
   * @param conf represents a configuration object
   * @param serverVrn represents the vrn associated with the server
   * @return a new [[Configuration]] object
   */
  def removeServerFromBackend(conf: Configuration, serverVrn: String) :  Configuration = {

    // Pump all he backends in to a new List, while filtering out the unwanted server
  val newBackends : List[Backend] = for ( backend <- conf.backends ) yield {
    Backend(backend.name,
            backend.mode,
            backend.servers.filter(srv => srv.name != serverVrn),
            backend.options
    )
  }
    conf.copy(backends = newBackends)
  }

  /**
   * Add a service to an existing configuration
   * @param conf represents a configuration object
   * @param service represent a [[Service]] object
   * @return a new [[Configuration]] object
   */
  def addService(conf: Configuration, service: Service): Configuration = {
    val _services = conf.services.getOrElse(List())
    conf.copy(services = Some(_services.::(service)))
  }
  /**
   * Remove a service from an existing configuration by vrn
   * @param conf represents a configuration object
   * @param vrn represents the vrn associated with the service
   * @return a new [[Configuration]] object
   */
  def removeService(conf: Configuration, vrn: String): Configuration = {
    val _services = conf.services.getOrElse(List())
    conf.copy(services = Some(_services.filterNot( srv => srv.name == vrn)))
  }



}