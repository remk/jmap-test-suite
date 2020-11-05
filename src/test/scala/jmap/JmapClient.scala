package jmap

import sttp.client.{RequestT, Response}
import sttp.client._
import sttp.client.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.model.HeaderNames
import zio.{Has, IO, ZLayer}


object Jmap {
  case class JmapServerConfiguration(imapPort: Int, jmapPort: Int, smtpPort: Int, url: String)

  trait Service {
    def mailboxGet(): IO[Throwable, Response[String]]
  }

  class JmapClient(configuration: JmapServerConfiguration) extends Service {

    def jamesRequest(request: RequestT[Identity, String, Nothing]): RequestT[Identity, String, Nothing] = {
      request.auth.bearer("bob")
        .header(HeaderNames.Accept, "application/json; jmapVersion=rfc-8621", replaceExisting = true)
    }

    def cyrusRequest(request: RequestT[Identity, String, Nothing]): RequestT[Identity, String, Nothing] = {
      request.auth.basic("bob", "bob")
        .header(HeaderNames.Accept, "application/json", replaceExisting = true)
    }

    override def mailboxGet(): IO[Throwable, Response[String]] = AsyncHttpClientZioBackend().flatMap { implicit backend => {
      val request: RequestT[Identity, String, Nothing] = basicRequest
        .post(uri"${configuration.url}")
        .contentType("application/json")
        .body(
          """
            | {
            |    "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
            |    "methodCalls": [[ "Mailbox/get", { }, "c1" ]]
            | }
            |""".stripMargin)
        .response(asStringAlways)

      backend.send(cyrusRequest(request))
    }
    }
  }

  val live: ZLayer[Has[JmapServerConfiguration], Nothing, Has[Service]] = ZLayer.fromService[JmapServerConfiguration, Service](configuration => new JmapClient(configuration))
}