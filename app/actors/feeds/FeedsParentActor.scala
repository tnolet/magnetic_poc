package actors.feeds

import akka.actor.{Props, Actor, ActorLogging}
import com.sclasen.akka.kafka.{AkkaConsumer, AkkaConsumerProps, CommitConfig}
import kafka.serializer.{StringDecoder, DefaultDecoder}
import org.I0Itec.zkclient.exception.ZkTimeoutException
import scala.concurrent.duration._
/**
* FeedsParentActor functions as the parent to all actors communicating with Kafka and other feeds suppliers
*/

trait FeedsMessages
case class StartFeeds(zkConnect: String) extends FeedsMessages

class FeedsParentActor extends Actor with ActorLogging {

  override def preStart() = {

  }

  def receive = {

    case s: StartFeeds =>
      log.info("Starting Kafka feeds")

      val lbMetricsFeed = context.actorOf(Props[LoadbalancerMetricsFeed], "lbMetricsFeed")

      val lbMetricsFeedCommitConfig = CommitConfig(
        commitInterval = Some(2 seconds),
        commitAfterMsgCount = Some(10),
        commitTimeout = 5 seconds
      )

      val consumerProps = AkkaConsumerProps.forContext(
        context = context,
        zkConnect = s.zkConnect,
        topic = "loadbalancer.all",
        group = "magnetic-lb-stream-consumer",
        streams = 1,
        keyDecoder = new DefaultDecoder(),
        msgDecoder = new StringDecoder(),
        receiver = lbMetricsFeed,
        commitConfig = lbMetricsFeedCommitConfig
      )

      val lbFeedsConsumer = new AkkaConsumer(consumerProps)
      try {
        lbFeedsConsumer.start()

      } catch {

        case zkt: ZkTimeoutException => log.error("Connecting to Zookeeper for feeds failed")
      }

  }

  override def postStop() = {


  }
}
