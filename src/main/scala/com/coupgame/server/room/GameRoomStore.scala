package com.coupgame.server.room

import akka.NotUsed
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source, SourceQueueWithComplete}
import com.coupgame.server.data.commands.{ActionCommand, ActionFeedbackCommand}
import com.coupgame.server.data.models.{ActionInterface, Card, Exchange, Hand}
import com.coupgame.server.data.store.PlayerStore

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

sealed trait ActionReview
case class Waiting() extends ActionReview
case class Approve(actionCommand: ActionCommand, reviewerId: Long) extends ActionReview
case class Block(actionCommand: ActionCommand, reviewerId: Long) extends ActionReview
case class Challenge(actionCommand: ActionCommand, reviewerId: Long) extends ActionReview

case class ActionLog(actionCommand: ActionCommand, reviews: Seq[ActionReview] = Seq.empty)

case class Game(gameId: Long) {
  private var connections: List[TextMessage => Unit] = List()
  def broadcast(queueOffer: TextMessage => Unit): Unit = {
    connections ::= queueOffer
  }
  def getConnections: List[TextMessage => Unit] = connections
}

class GameRoomStore(playerStore: PlayerStore)(implicit ec: ExecutionContext) {

  private var browserConnections: Map[Long, Game] = Map.empty
  private var actionsMap: mutable.Map[ActionCommand, ActionLog] = mutable.Map.empty

  def listen(gameId: Long): Flow[Message, Message, NotUsed] = {

    val inbound: Sink[Message, Any] = Sink.foreach(_ => ())
    val outbound: Source[Message, SourceQueueWithComplete[Message]] = Source.queue[Message](16, OverflowStrategy.fail)

    Flow.fromSinkAndSourceMat(inbound, outbound) { (_, outboundMat) => {
        browserConnections(gameId).broadcast(outboundMat.offer)
        NotUsed
      }
    }
  }

  def sendForReview(action: ActionCommand, gameId: Long, numOpponents: Int): Unit = {
    actionsMap += action -> ActionLog(action, reviews = (1 to numOpponents).map(_ => Waiting()))
    for (connection <- browserConnections(gameId).getConnections) connection(TextMessage.Strict(action.toJson))
  }

  def createGameRoom(): Long = {
    browserConnections = Map.empty
    val gameId: Long = if(browserConnections.keySet.isEmpty) 1L else browserConnections.keySet.max
    browserConnections += gameId -> Game(gameId)
    gameId
  }

  def dropFirstMatch[A](ls: Seq[A], value: A): Seq[A] = {
    val index = ls.indexOf(value)  //index is -1 if there is no match
    if (index < 0) {
      ls
    } else if (index == 0) {
      ls.tail
    } else {
      // splitAt keeps the matching element in the second group
      val (a, b) = ls.splitAt(index)
      a ++ b.tail
    }
  }

  def reviewFeedback(feedbackCommand: ActionFeedbackCommand, reviewerHand: Option[Hand], initiatorHand: Option[Hand]): Future[Seq[ActionCommand]] = {
    val actionReview: ActionReview = feedbackCommand.feedbackActionId match {
      case 1 => Approve(feedbackCommand.actionCommand, feedbackCommand.reviewerId)
      case 2 => Block(feedbackCommand.actionCommand, feedbackCommand.reviewerId)
      case 3 => Challenge(feedbackCommand.actionCommand, feedbackCommand.reviewerId)
    }

    actionReview match {
      case Challenge(_, _) =>
        val actionCard: Card = ActionInterface().getValidCard(feedbackCommand.actionCommand.actionId)
        val challenger: Long =  feedbackCommand.reviewerId
        val challengee = feedbackCommand.actionCommand.initiator

        val challengeSuccessful: Boolean = initiatorHand match {
          case Some(iHand) =>
            !Seq(iHand.cards._1, iHand.cards._2).filterNot(_.shown).exists(_.equals(actionCard))
        }

        if (challengeSuccessful) {
          playerStore.losePlayerInfluence(challengee).map { _ => Seq.empty }
        } else {
          playerStore.losePlayerInfluence(challenger).map { _ =>
            actionsMap.remove(feedbackCommand.actionCommand)
            Seq(feedbackCommand.actionCommand, ActionCommand(challengee, None, 6))
          }
        }

      case Approve(_, _) =>
        val currentLog: Option[ActionLog] = actionsMap.get(feedbackCommand.actionCommand)

        currentLog match {
          case Some(log) =>
            val newLog = log.copy(reviews = dropFirstMatch[ActionReview](log.reviews, Waiting()) ++ Seq(actionReview))
            actionsMap.update(feedbackCommand.actionCommand, newLog)
          case _ =>
        }

        val approvedActions = actionsMap.filter(kv => kv._2.reviews.forall {
          case Approve(_, _) => true
          case _ => false
        }).keys

        approvedActions.map(actionsMap.remove(_))

        Future { approvedActions.toSeq }
    }
  }

}