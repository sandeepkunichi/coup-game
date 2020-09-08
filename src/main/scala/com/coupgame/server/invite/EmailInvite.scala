package com.coupgame.server.invite

import com.sendgrid._

import scala.util.Try


object EmailInvite {

  def sendInvite(email: String, playerHash: String): Unit = {

    val apiKey = sys.env.getOrElse("SENDGRID_API_KEY", "")
    val from = new Email("sandeepkunichi@gmail.com")
    val subject = "Coup Game Invitation"
    val to = new Email(email)
    val content = new Content("text/plain", "Go to this link to play: https://coup-fe.herokuapp.com/player?id=" + playerHash)
    val mail = new Mail(from, subject, to, content)

    val sg = new SendGrid(apiKey)
    val request = new Request()

    Try {
      request.setMethod(Method.POST)
      request.setEndpoint("mail/send")
      request.setBody(mail.build)
      sg.api(request)
    }.getOrElse(throw new RuntimeException("Invite failed"))

  }

}