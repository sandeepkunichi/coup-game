package com.coupgame.server.data.json

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.coupgame.server.data.commands._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object JsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val startGameCommand: RootJsonFormat[StartGameCommand] = jsonFormat1(StartGameCommand)
  implicit val dealCommand: RootJsonFormat[DealCommand] = jsonFormat0(DealCommand)
  implicit val actionCommand: RootJsonFormat[ActionCommand] = jsonFormat3(ActionCommand)
  implicit val counterActionCommand: RootJsonFormat[CounterActionCommand] = jsonFormat3(CounterActionCommand)
  implicit val losInfluenceCommand: RootJsonFormat[LoseInfluenceCommand] = jsonFormat1(LoseInfluenceCommand)
  implicit val actionFeedbackCommand: RootJsonFormat[ActionFeedbackCommand] = jsonFormat3(ActionFeedbackCommand)

  implicit val response: RootJsonFormat[Response] = jsonFormat1(Response)
}
