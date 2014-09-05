package lib.util.vamp


/**
 * Created by tim on 05/09/14.
 */
object Naming {

  def createVrn(resourceType: String, environment: String) : String =  {

    "vamp"          +
    "-"             +
    environment     +
    "-"             +
    resourceType    +
      "-"             +
    Number.rnd

  }

}
