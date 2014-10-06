package lib.feeds

import actors.feeds.{StartFeeds, FeedsParentActor}
import akka.actor.{ActorRef, Props}
import com.typesafe.config.ConfigFactory
import play.api.libs.concurrent.Akka
import play.api.Play.current

/**
 * The Feeds class takes care of starting the Akka system that handles feeds from Kafka and other feed providers
 */
class Feeds {

  val conf = ConfigFactory.load()
  val zkHost = conf.getString("kafka.zookeeper.host")
  val zkPort = conf.getInt("kafka.zookeeper.port")
  val zkBaseUri = s"$zkHost:$zkPort"
  var feedsParent : ActorRef = _

  def startFeedsParent() : ActorRef = {

    this.feedsParent = Akka.system.actorOf(Props[FeedsParentActor], "feedsParent")
    feedsParent

  }

  def startFeeds = {

    feedsParent ! StartFeeds(zkConfig = zkBaseUri)

  }

}
