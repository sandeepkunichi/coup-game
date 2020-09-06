package com.coupgame.server.room

import akka.NotUsed
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source, SourceQueueWithComplete}
import com.coupgame.server.data.commands.ActionCommand

case class Game(gameId: Long) {
  private var connections: List[TextMessage => Unit] = List()
  def broadcast(queueOffer: TextMessage => Unit): Unit = {
    connections ::= queueOffer
  }
  def getConnections: List[TextMessage => Unit] = connections
}

class GameRoomStore() {

  private var browserConnections: Map[Long, Game] = Map.empty

  def listen(gameId: Long): Flow[Message, Message, NotUsed] = {

    val inbound: Sink[Message, Any] = Sink.foreach(_ => ())
    val outbound: Source[Message, SourceQueueWithComplete[Message]] = Source.queue[Message](16, OverflowStrategy.fail)

    Flow.fromSinkAndSourceMat(inbound, outbound) { (_, outboundMat) => {
        browserConnections(gameId).broadcast(outboundMat.offer)
        NotUsed
      }
    }
  }

  def sendForReview(action: ActionCommand, gameId: Long): Unit = {
    for (connection <- browserConnections(gameId).getConnections) connection(TextMessage.Strict(action.log))
  }

  def createGameRoom(): Long = {
    val gameId: Long = if(browserConnections.keySet.isEmpty) 1L else browserConnections.keySet.max
    browserConnections += gameId -> Game(gameId)
    gameId
  }
}