package com.coupgame.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.coupgame.server.data.commands._
import com.coupgame.server.data.json.JsonSupport._
import com.coupgame.server.data.json._
import com.coupgame.server.data.models.ActionInterface
import com.coupgame.server.data.store.LocalPlayerStore
import com.coupgame.server.room.GameRoomStore
import play.twirl.api.Html

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source

case class GameServerSocket(protocol: String, host: String, port: String)

trait CoupGameService {

  Seq("duke", "assassin", "captain", "contessa", "coup", "ambassador").foreach { img =>
    Source.fromResource(s"$img.jpg")
  }

  implicit val actorSystem: ActorSystem
  implicit val materializer: ActorMaterializer

  implicit val twirlHtmlMarshaller: ToEntityMarshaller[Html] =
    Marshaller.StringMarshaller.wrap(MediaTypes.`text/html`)(_.toString)

  private val playerStore = new LocalPlayerStore()
  private val gameRoomStore = new GameRoomStore(playerStore)

  protected val gameServerSocket: GameServerSocket


  val startGame: Route = path("start") {
    post {
      entity(as[StartGameCommand]) { sgc =>
        complete {
          (for {
            players <- playerStore.createPlayers(sgc.emails)
            currentPlayerToSet <- playerStore.getNextTurn
          } yield (players, currentPlayerToSet))
          .map {
            case (players, currentPlayerToSet) =>
              playerStore.currentPlayer = Some(currentPlayerToSet)
              val gameId = gameRoomStore.createGameRoom()
              StartGameResponse.apply(gameId, players)
          }
        }
      }
    }
  }

  val deal: Route = path("deal") {
    post {
      entity(as[DealCommand]) { _ =>
        playerStore.dealToPlayers()
        complete(DealResponse.apply())
      }
    }
  }

  val action: Route = path("action") {
    post {
      entity(as[ActionCommand]) { ac =>
        complete {
          playerStore
            .executeAction(ActionInterface().getActionWithId(ac.actionId), ac.initiator, ac.target)
            .map(_ => ActionResponse.apply(ac.log))
        }
      }
    }
  }

  val counterAction: Route = path("counter") {
    post { entity(as[CounterActionCommand]) {cac => complete(CounterActionResponse.apply(cac.log)) } }
  }

  val world: Route = path("world") {
    get { complete(playerStore.getWorld.map(WorldResponse.apply)) }
  }

  val loseInfluence: Route = path("lose") {
    post {
      entity(as[LoseInfluenceCommand]) { lic =>
        complete(playerStore.losePlayerInfluence(lic.playerId).map(LoseInfluenceResponse.apply))
      }
    }
  }

  val playerView: Route = path("player") {
    get {
      parameter('id.as[String]) { playerId =>
        complete {
          for {
            player <- playerStore.getPlayerByHash(playerId)
            world <- playerStore.getWorld
          } yield {
            player match {
              case Some(p) => com.coupgame.app.html.player_view.render(p, world, gameServerSocket, playerStore.currentPlayer.getOrElse(throw new Exception(s"Something went wrong")))
              case _ => com.coupgame.app.html.error.render("Error getting player view")
            }
          }
        }
      }
    }
  }

  val index: Route = path("game") {
    get {
      complete(com.coupgame.app.html.index.render())
    }
  }

  val gameRoom: Route = path("game-room") {
    get {
      parameter('gameId.as[Long]) { gameId =>
        handleWebSocketMessages(gameRoomStore.listen(gameId))
      }
    }
  }

  val postAction: Route = path("post-action") {
    post { entity(as[ActionCommand]) { ac: ActionCommand =>
      parameter('gameId.as[Long]) { gameId =>
        playerStore.getWorld.map { world =>
          gameRoomStore.sendForReview(ac, gameId, world.size - 1)
        }
        complete("Sent to players")
      }
      }
    }
  }

  val feedback: Route = path("feedback") {
    post {
      entity(as[ActionFeedbackCommand]) { afc =>
        (for {
          reviewerHand <- playerStore.getPlayer(afc.reviewerId).map(_.hand)
          initiatorHand <- playerStore.getPlayer(afc.actionCommand.initiator).map(_.hand)
        } yield gameRoomStore.reviewFeedback(afc, reviewerHand, initiatorHand)).flatMap { approvedActionsFuture =>
          approvedActionsFuture.flatMap { approvedActions =>
            Future.sequence {
              approvedActions.map { ac =>
                playerStore.executeAction(ActionInterface().getActionWithId(ac.actionId), ac.initiator, ac.target)
              }
            }
          }
        }
        complete("Feedback collected")
      }
    }
  }

  val images: Route = path("images" / Segment) { name =>
    getFromResource(s"$name.jpg")
  }

}

class GameServer(gameServerSocketProd: GameServerSocket)
                (implicit val actorSystem: ActorSystem, implicit val materializer: ActorMaterializer) extends CoupGameService {

  override protected val gameServerSocket: GameServerSocket = gameServerSocketProd

  def startServer(address: String, port: Int): Future[Http.ServerBinding] = {
    Http().bindAndHandle(startGame ~ deal ~ world ~ playerView ~ gameRoom ~ postAction ~ feedback ~ index ~ images, address, port)
  }
}

object GameServerMain {

  val gameServerSocket: GameServerSocket = sys.env.get("PORT") match {
    case Some(_) => GameServerSocket("wss", "coup-fe.herokuapp.com", "")
    case _ => GameServerSocket("ws", "localhost", "8080")
  }

  def main(args: Array[String]) {

    val port: Int = sys.env.getOrElse("PORT", "8080").toInt

    implicit val actorSystem: ActorSystem = ActorSystem("coup-game-server")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val server = new GameServer(gameServerSocket)
    server.startServer("0.0.0.0", port)
  }

}