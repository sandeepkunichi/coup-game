package com.coupgame.server.data.commands

import com.coupgame.server.data.models.ActionInterface

sealed trait GameCommand

case class StartGameCommand(numPlayers: Int) extends GameCommand
case class DealCommand() extends GameCommand
case class ActionCommand(initiator: Long, target: Option[Long], actionId: Int) extends GameCommand {
  def log: String = {
    s"Player $initiator executed action ${ActionInterface().getActionWithId(actionId)}" + {
      target match {
        case Some(player) => s" on player $player"
        case None => ""
      }
    }
  }
}
case class CounterActionCommand(initiator: Long, target: Long, counterActionId: Int) extends GameCommand {
  def log: String = {
    s"Player $initiator executed counter-action ${ActionInterface().getCounterActionWithId(counterActionId).log} on player $target"
  }
}
case class LoseInfluenceCommand(playerId: Long, cardId: Int) extends GameCommand