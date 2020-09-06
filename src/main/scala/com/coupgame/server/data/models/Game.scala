package com.coupgame.server.data.models

case object Game {
  def createGame(numPlayers: Int): Set[Long] = (1L to numPlayers.toLong).toSet[Long]
}

case class Player(playerId: Long, hand: Option[Hand] = None, coins: Int) {
  def getHand: String = {
    hand match {
      case Some(h) if h.cards._1.shown & !h.cards._2.shown => h.cards._1.getClass.getSimpleName + ",****"
      case Some(h) if h.cards._2.shown & !h.cards._1.shown => h.cards._2.getClass.getSimpleName + ",****"
      case Some(h) if h.cards._2.shown & h.cards._1.shown => h.cards._1.getClass.getSimpleName + "," + h.cards._2.getClass.getSimpleName
      case Some(_) => "****,****"
      case _ => ""
    }
  }
}

case class ActionInterface() {
  lazy val actionIdMap = Map (
    1 -> Income,
    2 -> ForeignAid,
    3 -> Coup,
    4 -> Tax,
    5 -> Assassinate,
    6 -> Exchange,
    7 -> Steal
  )

  lazy val counterActionMap = Map (
    1 -> BlockForeignAid,
    2 -> BlockStealing,
    3 -> BlockAssassination,
    4 -> Challenge
  )

  def getActionWithId(actionId: Int): Action = actionIdMap.getOrElse(actionId, throw new RuntimeException(s"$actionId not found"))
  def getCounterActionWithId(counterActionId: Int): CounterAction = counterActionMap.getOrElse(counterActionId, throw new RuntimeException(s"$counterActionId not found"))
}

class Action(description: String) {
  def log: String = description
}

case object Income extends Action("Take 1 coin from bank")
case object ForeignAid extends Action("Take 2 coins from bank")
case object Coup extends Action("Pay 7 coins and coup player")
case object Tax extends Action("Take 3 coins from bank")
case object Assassinate extends Action("Pay 3 coins and assassinate player")
case object Exchange extends Action("Exchange cards with Court Deck")
case object Steal extends Action("Take 2 coins from player")

class CounterAction(description: String) {
  def log: String = description
}

case object BlockForeignAid extends CounterAction("Block foreign aid")
case object BlockStealing extends CounterAction("Block stealing")
case object BlockAssassination extends CounterAction("Block assassination")
case object Challenge extends CounterAction("Challenge player")