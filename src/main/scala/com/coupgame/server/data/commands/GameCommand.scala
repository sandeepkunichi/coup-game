package com.coupgame.server.data.commands

import com.coupgame.server.data.models.ActionInterface

import spray.json._

sealed trait GameCommand

case class StartGameCommand(emails: Set[String]) extends GameCommand
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
  def toJson: String = {
    s"""{"initiator": $initiator, ${target.map(t => s""""target": $t, """).getOrElse("")} "actionId": $actionId, "log": "$log"}""".parseJson.toString
  }
}
case class CounterActionCommand(initiator: Long, target: Long, counterActionId: Int) extends GameCommand {
  def log: String = {
    s"Player $initiator executed counter-action ${ActionInterface().getCounterActionWithId(counterActionId).log} on player $target"
  }
}
case class LoseInfluenceCommand(playerId: Long) extends GameCommand
case class ActionFeedbackCommand(actionCommand: ActionCommand, feedbackActionId: Int, reviewerId: Long) extends GameCommand