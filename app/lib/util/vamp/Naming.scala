package lib.util.vamp


/**
 * Created by tim on 05/09/14.
 */
object Naming {

  def createVrn(resourceType: String, environment: String = "none") : String =  {

    environment match {

      case "none" =>  "vrn" + "-" + resourceType + "-" + Number.rnd

      case _ => "vrn" + "-" + environment + "-" + resourceType + "-" + Number.rnd

    }
  }
}
