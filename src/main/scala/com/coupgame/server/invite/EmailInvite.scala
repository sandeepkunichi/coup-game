package com.coupgame.server.invite

import com.sendgrid._

import scala.util.Try


object EmailInvite {

  def sendEmailInvite(email: String, playerHash: String): Unit = {
    val apiKey = sys.env.getOrElse("SENDGRID_API_KEY", "")
    val from = new Email("sandeepkunichi@gmail.com")
    val subject = "Coup Game Invitation"
    val to = new Email(email)
    val htmlEmail = com.coupgame.email.html.email_invite.render(playerHash).toString
    val content = new Content("text/html", htmlEmail)
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

  def printInvite(playerHash: String): Unit = {
    println(s"http://localhost:8080/player?id=$playerHash")
  }

  def sendInvite(email: String, playerHash: String): Unit = {
    sys.env.get("ENV") match {
      case Some(_) => sendEmailInvite(email, playerHash)
      case _ => printInvite(playerHash)
    }
  }

}