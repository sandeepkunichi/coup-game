package com.coupgame.server.data.store

import java.util.UUID

import com.coupgame.server.data.models._
import com.coupgame.server.invite.EmailInvite

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

trait PlayerStore {
  var currentPlayer: Option[Player]
  def getPlayer(playerId: Long): Future[Player]
  def getPlayerByHash(playerHash: String): Future[Option[Player]]
  def createPlayers(emails: Set[String]): Future[Set[Player]]
  def dealToPlayers(): Future[Unit]
  def getWorld: Future[Set[Player]]
  def losePlayerInfluence(playerId: Long): Future[Player]
  def executeAction(action: Action, initiator: Long, target: Option[Long]): Future[Unit]
  def getNextTurn: Future[Player]
  def deductCoins(playerId: Long, numCoins: Int): Future[Unit]
}

class LocalPlayerStore(implicit executionContext: ExecutionContext) extends PlayerStore {

  private var players: mutable.Map[Long, Player] = mutable.Map.empty
  private var turns: Iterator[Player] = Iterator.empty
  override var currentPlayer: Option[Player] = None

  override def getPlayer(playerId: Long): Future[Player] = Future {
    players.getOrElse(playerId, throw new RuntimeException(s"$playerId not found"))
  }

  override def getPlayerByHash(playerHash: String): Future[Option[Player]] = Future {
    players.values.find(p => p.playerHash.equals(UUID.fromString(playerHash)))
  }

  override def createPlayers(emails: Set[String]): Future[Set[Player]] = {
    val playerIds: Set[Long] = Game.createGame(emails.size)
    Future {
      players = mutable.Map.empty
      playerIds.zip(emails).foreach {
        case (playerId: Long, email: String) =>
          val newPlayer = Player(playerId, coins = 2, playerHash = UUID.randomUUID(), email = email)
          players += playerId -> newPlayer
      }
      turns = Iterator.continually(players.values).flatten
      players.values.foreach { player =>
        EmailInvite.sendInvite(player.email, player.playerHash.toString)
      }
      players.values.toSet
    }
  }

  override def dealToPlayers(): Future[Unit] = {
    Future {
      players.values.foreach { player =>
        players.update(player.playerId, Player(playerId = player.playerId, coins = 2, hand = Some(Deck.deal), playerHash = player.playerHash, email = player.email))
      }
    }
  }

  override def getWorld: Future[Set[Player]] = {
    println(players.values)
    Future {
      players.values.toSet
    }
  }

  override def losePlayerInfluence(playerId: Long): Future[Player] = {
    Future {
      val player = players.getOrElse(playerId, throw new RuntimeException(s"$playerId not found"))
      val hand: (Card, Card) = player.hand.getOrElse(throw new RuntimeException(s"Player hand is empty")).cards
      val lostCard: Option[Card] = Random.shuffle(Seq(hand._1, hand._2).filterNot(_.shown)).headOption

      if (lostCard.isDefined) {
        val newHand: Option[Hand] = player.hand.map(_.cards) match {
          case Some(cards: (Card, Card)) if cards._1.equals(lostCard.get) => Some(Hand((cards._2, cards._1.withShown)))
          case Some(cards: (Card, Card)) if cards._2.equals(lostCard.get) => Some(Hand((cards._1, cards._2.withShown)))
          case _ => player.hand
        }
        players.update(playerId, Player(playerId = playerId, coins = player.coins, hand = newHand, playerHash = player.playerHash, email = player.email))
      }


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
          if (initiatorPlayer.coins < 7) {
            throw new RuntimeException(s"Cannot coup. Player $initiator does not have enough coins")
          }
          targetPlayer match {
            case Some(targetPlayerFound) =>
              val newHand: Option[Hand] = targetPlayerFound.hand.map(h => (h.cards._1.withShown, h.cards._2.withShown)).map(Hand)
              players.update(targetPlayerFound.playerId, targetPlayerFound.copy(hand = newHand))
            case _ =>
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
            case _ =>
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
            case _ =>
          }
      }
    }
  }

  override def getNextTurn: Future[Player] = Future {
    turns.take(1).toSeq.head
  }

  override def deductCoins(playerId: Long, numCoins: Int): Future[Unit] = {
    getPlayer(playerId).map { player =>
      if (player.coins < numCoins) {
        throw new RuntimeException(s"Cannot deduct. Player $player does not have enough coins")
      }
      players.update(player.playerId, player.copy(coins = player.coins - numCoins))
    }
  }
}
