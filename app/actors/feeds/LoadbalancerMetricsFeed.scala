package actors.feeds

import akka.actor.{ActorLogging, Actor}
import com.sclasen.akka.kafka.StreamFSM
import lib.kairosdb.KairosDB
import models.kairosdb.DataPoint
import models.{GetMetrics, MetricsFeed}
import play.api.libs.iteratee.Concurrent
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import scala.concurrent.ExecutionContext.Implicits.global

class LoadbalancerMetricsFeed extends Actor with ActorLogging {

  override def preStart() = {



  }


  val (enum, channel) = Concurrent.broadcast[String]

  def receive = {


   case data : String =>

     import models.kairosdb.DataPointJson.datapointReads

     val payload = Json.parse(data)

     payload.validate[DataPoint].fold(
       valid = {
         datapoint => {

           // Add tags to the datapoint object for Kairos
           val names = datapoint.name.split("\\.")

           val source = "loadbalancer"
           val proxy = names(0)
           val proxyType = names(1)
           val metric = names(2)

           // KairosDB wants epoch timestamps in milliseconds
           val timestamp = datapoint.timestamp * 1000

           val tags = Map( "source" -> source, "proxy" -> proxy, "proxyType" -> proxyType, "metric" -> metric)
           val dataPointWithTags = datapoint.copy(tags = Some(tags), timestamp = timestamp)


           KairosDB.setDataPoint(dataPointWithTags).map {
             case true =>
               log.debug("Succesfully committed metric to Kairos")
             case false =>
               log.debug("Error writing metric to Kairos")
           }

         }
       },
       invalid = {
         errors => log.error(s"Error parsing metric: " + errors)
       }
     )
     channel.push(data)

     sender ! StreamFSM.Processed

   case GetMetrics =>
     sender ! MetricsFeed(this.enum)
 }

}
