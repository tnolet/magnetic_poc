package actors.sla

import akka.actor.{ActorRef, Actor, ActorLogging}
import lib.vamp.pulse.ScoreBoard
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * ScoreBoardActor takes care of grabbing the score board published by the Vamp Pulse service
 */

trait ScoreBoardMessage
case class GetScore(metric: String) extends ScoreBoardMessage
case class Score(value: Long) extends ScoreBoardMessage

class ScoreBoardActor extends Actor with ActorLogging {

  var originalSender : ActorRef = _

  def receive = {

    case s: GetScore =>

      originalSender = sender()

      ScoreBoard.getScoreBoard.map {

        case Some(sb: Map[String,Long]) =>


          // check if the requested metric is in the score board. It might not yet be
          // due to timing issues. If it is not there, we report -1
          if (sb.contains(s.metric)) {
            originalSender ! Score(value = sb(s.metric))
          } else {
            originalSender ! Score(value = -1)
          }

        case None =>
          sender ! Score(value = -1)

      }
  }

}
