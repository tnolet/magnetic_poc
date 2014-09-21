package actors.loadbalancer

import akka.actor.{ActorLogging, Actor}
import lib.loadbalancer.LoadBalancer
import models.MetricsFeed
import play.api.libs.iteratee.Concurrent
import play.api.libs.json._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Future


sealed trait LbMetricsMessage
case object GetMetrics extends LbMetricsMessage
case object ReadMetrics extends LbMetricsMessage
case object Start extends LbMetricsMessage

/**
 *
 * LoadBalancerMetricsActor pushes out load balancer metrics in the form of an Iteratee.
 * The load balancer stats endpoint is polled on a regular schedule, defined by the scheduler
 */

class LoadBalancerMetricsActor extends Actor with ActorLogging {

  //var channel : Concurrent.Channel[JsValue] = _
  val (enum, channel) = Concurrent.broadcast[JsValue]

  def receive = {

    case Start =>
      log.debug("Starting load balancer metrics feed")
      readMetricsFromLb
    case GetMetrics => sender ! MetricsFeed(this.enum)
    case ReadMetrics => readMetricsFromLb
    case _ =>
  }


  private def readMetricsFromLb = {

    val futureStats : Future[JsValue] = LoadBalancer.getStats
    futureStats.map {
      case stats: JsValue =>
        channel.push(stats)
        reschedule()

      case _ =>
        log.error("Could not get loadbalancer stats")
        reschedule()
    }
  }

  private def reschedule() : Unit = {
    // Schedule another check
    context.system.scheduler.scheduleOnce(3 seconds, self, ReadMetrics)
  }
}
