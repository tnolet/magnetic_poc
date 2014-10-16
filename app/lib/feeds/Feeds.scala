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
  val zkConnect = conf.getString("kafka.zookeeper.connect")
  var feedsParent : ActorRef = _

  def startFeedsParent() : ActorRef = {

    this.feedsParent = Akka.system.actorOf(Props[FeedsParentActor], "feedsParent")
    feedsParent

  }

  def createConsumer = {

  }

  def startFeeds = {

    feedsParent ! StartFeeds(zkConnect = zkConnect)

  }

}
