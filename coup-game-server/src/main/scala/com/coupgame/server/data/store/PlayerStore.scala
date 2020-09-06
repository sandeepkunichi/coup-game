package com.coupgame.server.data.store

import com.coupgame.server.data.models._

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

trait PlayerStore {
  def createPlayers(numPlayers: Int): Future[Unit]
  def dealToPlayers(): Future[Unit]
  def getWorld: Future[Set[Player]]
  def losePlayerInfluence(playerId: Long, cardId: Int): Future[Player]
  def executeAction(action: Action, initiator: Long, target: Option[Long]): Future[Unit]
}

class LocalPlayerStore(implicit executionContext: ExecutionContext) extends PlayerStore {

  private var players: mutable.Map[Long, Player] = mutable.Map.empty

  override def createPlayers(numPlayers: Int): Future[Unit] = {
    val playerIds: Set[Long] = Game.createGame(numPlayers)
    Future {
      playerIds.foreach { playerId =>
        players += playerId -> Player(playerId, coins = 2)
      }
    }
  }

  override def dealToPlayers(): Future[Unit] = {
    Future {
      players.keys.foreach { playerId =>
        players.update(playerId, Player(playerId = playerId, coins = 2, hand = Some(Deck.deal)))
      }
    }
  }

  override def getWorld: Future[Set[Player]] = {
    println(players.values)
    Future {
      players.values.toSet
    }
  }

  override def losePlayerInfluence(playerId: Long, cardId: Int): Future[Player] = {
    Future {
      val player = players.getOrElse(playerId, throw new RuntimeException(s"$playerId not found"))
      val lostCard: Card = CardInterface().getCardWithId(cardId)
      val newHand: Option[Hand] = player.hand.map(_.cards) match {
        case Some(cards: (Card, Card)) if cards._1.equals(lostCard) => Some(Hand((cards._2, cards._1.withShown)))
        case Some(cards: (Card, Card)) if cards._2.equals(lostCard) => Some(Hand((cards._1, cards._2.withShown)))
        case _ => player.hand
      }
      players.update(playerId, Player(playerId = playerId, coins = player.coins, hand = newHand))

      players.getOrElse(playerId, throw new RuntimeException(s"$playerId not found"))
    }
  }

  override def executeAction(action: Action, initiator: Long, target: Option[Long]): Future[Unit] = {
    Future {
      val initiatorPlayer: Player = players.getOrElse(initiator, throw new RuntimeException(s"$initiator not found"))
      val targetPlayer: Option[Player] = target.map(players.getOrElse(_, throw new RuntimeException(s"$initiator not found")))
      action match {
        case Income =>
          players.update(initiator, initiatorPlayer.copy(coins = initiatorPlayer.coins + 1))
        case ForeignAid =>
          players.update(initiator, initiatorPlayer.copy(coins = initiatorPlayer.coins + 2))
        case Coup =>
          targetPlayer match {
            case Some(targetPlayerFound) =>
              val newHand: Option[Hand] = targetPlayerFound.hand.map(h => (h.cards._1.withShown, h.cards._2.withShown)).map(Hand)
              players.update(targetPlayerFound.playerId, targetPlayerFound.copy(hand = newHand))
          }
        case Tax =>
          players.update(initiator, initiatorPlayer.copy(coins = initiatorPlayer.coins + 3))
        case Assassinate =>
          if (initiatorPlayer.coins < 3) {
            throw new RuntimeException(s"Cannot assassinate. Player $initiator does not have enough coins")
          }
          players.update(initiator, initiatorPlayer.copy(coins = initiatorPlayer.coins - 3))
          targetPlayer match {
            case Some(targetPlayerFound) =>
              val newHand: Option[Hand] = targetPlayerFound.hand.map(h => {
                h.cards match {
                  case cards: (Card, Card) if !cards._1.shown & !cards._2.shown => (cards._1.withShown, cards._2)
                  case _  => (h.cards._1.withShown, h.cards._2.withShown)
                }
              }).map(Hand)
              players.update(targetPlayerFound.playerId, targetPlayerFound.copy(hand = newHand))
          }
        case Exchange =>
          val playerCards: (Card, Card) = initiatorPlayer.hand.getOrElse(throw new RuntimeException(s"Player $initiator has no cards to exchange")).cards
          val topCards: (Card, Card) = Deck.deal.cards
          val totalCards: Seq[Card] = Random.shuffle(Seq(playerCards._1, playerCards._2, topCards._1, topCards._2).filterNot(_.shown))
          val numCards: Int = Seq(playerCards._1, playerCards._2).filterNot(_.shown).size
          val newCards = (totalCards.take(numCards) ++ Seq(playerCards._1, playerCards._2).filter(_.shown)).take(2)
          val newHand: Option[Hand] = Some(Hand(cards = (newCards.head, newCards.last)))
          players.update(initiator, initiatorPlayer.copy(hand = newHand))
        case Steal =>
          targetPlayer match {
            case Some(targetPlayerFound) =>
              val targetPlayerCoins = targetPlayerFound.coins
              players.update(targetPlayerFound.playerId, targetPlayerFound.copy(coins = Math.max(0, targetPlayerFound.coins - 2)))
              players.update(initiator, initiatorPlayer.copy(coins = initiatorPlayer.coins + Math.min(2, targetPlayerCoins)))
          }
      }
    }
  }
}
