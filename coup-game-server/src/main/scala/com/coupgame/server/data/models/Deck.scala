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
  def withShown: Card = this match {
    case _: Captain => Captain(true)
    case _: Ambassador => Ambassador(true)
    case _: Duke => Duke(true)
    case _: Assassin => Assassin(true)
    case _: Contessa => Contessa(true)
  }
}

case class Captain(override val shown: Boolean = false) extends Card
case class Ambassador(override val shown: Boolean = false) extends Card
case class Duke(override val shown: Boolean = false) extends Card
case class Assassin(override val shown: Boolean = false) extends Card
case class Contessa(override val shown: Boolean = false) extends Card

case class Hand(cards: (Card, Card))

case object Deck {

  private val cards: List[Card] = List(Captain(), Captain(), Ambassador(), Ambassador(), Duke(), Duke(), Assassin(), Assassin(), Contessa(), Contessa())

  def shuffle(): List[Card] = Random.shuffle(cards)

  def deal: Hand = shuffle().takeRight(2) match {
    case List(cardA, cardB) => Hand(cardA, cardB)
  }

}