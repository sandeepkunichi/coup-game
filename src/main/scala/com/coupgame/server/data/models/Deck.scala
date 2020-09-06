package com.coupgame.server.data.models

import scala.util.Random

case class CardInterface() {
  lazy val cardIdMap = Map (
    1 -> Captain(),
    2 -> Ambassador(),
    3 -> Duke(),
    4 -> Assassin(),
    5 -> Contessa()
  )

  def getCardWithId(cardId: Int): Card = cardIdMap.getOrElse(cardId, throw new RuntimeException(s"$cardId not found"))
}

sealed trait Card {
  val shown: Boolean = false
  val description: String
  def withShown: Card = this match {
    case _: Captain => Captain(shown = true)
    case _: Ambassador => Ambassador(shown = true)
    case _: Duke => Duke(shown = true)
    case _: Assassin => Assassin(shown = true)
    case _: Contessa => Contessa(shown = true)
  }
}

case class Captain(override val shown: Boolean = false, override val description: String = "Take 2 coins from another player. Blocks stealing.") extends Card
case class Ambassador(override val shown: Boolean = false, override val description: String = "Exchange cards with Court Deck. Blocks stealing.") extends Card
case class Duke(override val shown: Boolean = false, override val description: String = "Take 3 coins. Blocks Foreign Aid") extends Card
case class Assassin(override val shown: Boolean = false, override val description: String = "Pay 3 coins and assassinate a player.") extends Card
case class Contessa(override val shown: Boolean = false, override val description: String = "Blocks assassination.") extends Card

case class Hand(cards: (Card, Card))

case object Deck {

  private val cards: List[Card] = List(Captain(), Captain(), Ambassador(), Ambassador(), Duke(), Duke(), Assassin(), Assassin(), Contessa(), Contessa())

  def shuffle(): List[Card] = Random.shuffle(cards)

  def deal: Hand = shuffle().takeRight(2) match {
    case List(cardA, cardB) => Hand(cardA, cardB)
  }

}