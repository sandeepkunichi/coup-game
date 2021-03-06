package com.coupgame.server.room

import akka.NotUsed
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source, SourceQueueWithComplete}
import com.coupgame.server.data.commands.{ActionCommand, ActionFeedbackCommand}
import com.coupgame.server.data.models.{ActionInterface, Card, CounterAction, Hand}
import com.coupgame.server.data.store.PlayerStore

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import spray.json._

sealed trait ActionReview
case class Waiting() extends ActionReview
case class Approve(actionCommand: ActionCommand, reviewerId: Long) extends ActionReview
case class Block(actionCommand: ActionCommand, reviewerId: Long) extends ActionReview
case class Challenge(actionCommand: ActionCommand, reviewerId: Long) extends ActionReview
case class ChallengeBlock(actionCommand: ActionCommand) extends ActionReview
case class ApproveBlock(actionCommand: ActionCommand, reviewerId: Long) extends ActionReview

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

  def sendForReview(action: ActionCommand, gameId: Long, numOpponents: Int, alternateLog: Option[String] = None, skip: Boolean = false): Unit = {
    if (!skip) {
      actionsMap += action -> ActionLog(action, reviews = (1 to numOpponents).map(_ => Waiting()))
    }
    for (connection <- browserConnections(gameId).getConnections) connection(TextMessage.Strict(alternateLog.getOrElse(action.toJson)))
  }

  def reloadAll(gameId: Long): Future[Unit] = {
    (for {
      currentPlayer <- playerStore.getNextTurn
    } yield {
      playerStore.currentPlayer = Some(currentPlayer)
    }).map { _ =>
      for (connection <- browserConnections(gameId).getConnections) connection(TextMessage.Strict("RELOAD"))
    }
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
      case 4 => ChallengeBlock(feedbackCommand.actionCommand)
      case 5 => ApproveBlock(feedbackCommand.actionCommand, feedbackCommand.reviewerId)
    }

    actionReview match {
      case Challenge(_, _) =>
        val actionCard: Card = ActionInterface().getValidCard(feedbackCommand.actionCommand.actionId)
        val challenger: Long =  feedbackCommand.reviewerId
        val challengee = feedbackCommand.actionCommand.initiator

        val challengeSuccessful: Boolean = initiatorHand match {
          case Some(iHand) =>
            !Seq(iHand.cards._1, iHand.cards._2).filterNot(_.shown).exists(_.equals(actionCard))
          case _ => false
        }

        if (challengeSuccessful) {
          playerStore.losePlayerInfluence(challengee).flatMap { _ =>
            reloadAll(1).map { _ =>
              Seq.empty[ActionCommand]
            }
          }
        } else {
          playerStore.losePlayerInfluence(challenger).flatMap { _ =>
            actionsMap.remove(feedbackCommand.actionCommand)
            reloadAll(1).map { _ =>
              Seq(feedbackCommand.actionCommand, ActionCommand(challengee, None, 6))
            }
          }
        }

      case ApproveBlock(_, _) =>
        val target: Option[Long] = feedbackCommand.actionCommand.actionId match {
          case 5 | 7 => Some(feedbackCommand.actionCommand.initiator)
          case _ => None
        }
        val originalAction = ActionCommand(feedbackCommand.reviewerId, target, feedbackCommand.actionCommand.actionId)
        actionsMap.remove(originalAction)


        reloadAll(1).flatMap { _ =>
          // If the original initiator (now reviewer of the block action)
          // accepts a block on assassination, deduct coins for assassination
          if (feedbackCommand.actionCommand.actionId == 5) {
            playerStore.deductCoins(feedbackCommand.reviewerId, 3).map { _ =>
              Seq.empty[ActionCommand]
            }
          } else {
            Future {
              Seq.empty[ActionCommand]
            }
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

        reloadAll(1).map { _ =>
          approvedActions.toSeq
        }

      case Block(_, _) =>
        Future {
          val actionCommand = feedbackCommand.actionCommand
          val counterAction = ActionInterface().getCounterForAction(actionCommand.actionId)
          val counterActionLog = s"""${feedbackCommand.reviewerId} executed ${counterAction.log}"""
          val log: String = s"""{"initiator": ${feedbackCommand.reviewerId}, "target": ${actionCommand.initiator}, "actionId": ${actionCommand.actionId}, "log": "$counterActionLog"}""".parseJson.toString
          sendForReview(feedbackCommand.actionCommand, 1, 1, Some(log), skip = true)
          Seq.empty
        }
      case ChallengeBlock(_) =>
        val counterAction: CounterAction = ActionInterface().getCounterForAction(feedbackCommand.actionCommand.actionId)
        val counterActionId: Int = ActionInterface().counterActionMap.filter(_._2.equals(counterAction)).keySet.head
        val counterActionCards: Set[Card] = ActionInterface().getValidCardsForCounter(counterActionId)
        val challenger: Long =  feedbackCommand.reviewerId
        val challengee = feedbackCommand.actionCommand.initiator

        val challengeSuccessful: Boolean = initiatorHand match {
          case Some(iHand) =>
            !Seq(iHand.cards._1, iHand.cards._2).filterNot(_.shown).exists(c => counterActionCards.exists(_.equals(c)))
          case _ => false
        }

        if (challengeSuccessful) {
          playerStore.losePlayerInfluence(challengee).flatMap { _ =>
            actionsMap.remove(feedbackCommand.actionCommand)
            val originalAction = ActionCommand(initiator = feedbackCommand.reviewerId, target = Some(feedbackCommand.actionCommand.initiator), actionId = feedbackCommand.actionCommand.actionId)
            reloadAll(1).map { _ =>
              Seq(originalAction) ++ (if (originalAction.actionId == 2) Seq.empty else Seq(ActionCommand(challenger, None, 6)))
            }
          }
        } else {
          playerStore.losePlayerInfluence(challenger).flatMap { _ =>
            actionsMap.remove(feedbackCommand.actionCommand)
            reloadAll(1).map { _ =>
              Seq.empty
            }
          }
        }
      case Waiting() => Future {
        Seq.empty
      }
    }
  }

}