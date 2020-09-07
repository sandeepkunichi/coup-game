package com.coupgame.server.data.json

import com.coupgame.server.data.models.Player

import spray.json._

case class Response(data: JsValue)

case object StartGameResponse {
  def apply(gameId: Long, players: Set[Player]): Response = Response(s"""{"message": "Game created", "gameId": $gameId, "players": [${players.map(p => s""""${p.playerHash.toString}"""").mkString(",")}]}""".parseJson)
}
case object DealResponse {
  def apply(): Response = Response("""{"message": "Dealing complete"}""".parseJson)
}

case object WorldResponse {
  def apply(world: Set[Player]): Response = {
    val players = world.map { player =>
      s"""{"playerId": ${player.playerId}, "hand": "${player.getHand}", "coins": "${player.coins}"}"""
    }
    Response(s"""{"players": [${players.mkString(",")}]}""".parseJson)
  }
}

case object LoseInfluenceResponse {
  def apply(player: Player): Response = {
    Response(s"""{"playerId": ${player.playerId}, "hand": "${player.getHand}", "coins": "${player.coins}"}""".parseJson)
  }
}

case object ActionResponse {
  def apply(log: String): Response = Response(s"""{"message": "$log"}""".parseJson)
}

case object CounterActionResponse {
  def apply(log: String): Response = Response(s"""{"message": "$log"}""".parseJson)
}