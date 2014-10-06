package actors.feeds

import akka.actor.{ActorLogging, Actor}
import com.sclasen.akka.kafka.StreamFSM
import models.{GetMetrics, MetricsFeed}
import play.api.libs.iteratee.Concurrent


class LoadbalancerMetricsFeed extends Actor with ActorLogging {

  val (enum, channel) = Concurrent.broadcast[String]

  def receive = {

   case metric : String =>
     channel.push(metric)

     sender ! StreamFSM.Processed

   case GetMetrics =>
     sender ! MetricsFeed(this.enum)
 }

}
