package jmap

import com.dimafeng.testcontainers.GenericContainer
import zio.json._
import jmap.Jmap.Service
import jmap.JmapSpec.JmapServerConfiguration
import org.apache.commons.net.imap.IMAPClient
import sttp.client._
import sttp.client.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.model.HeaderNames
import zio.test.Assertion._
import zio.test._
import zio.{Has, IO, RIO, Task, UIO, ZIO, ZLayer, ZManaged}
import TestAspect.ignore
import com.dimafeng.testcontainers

object Jmap {

  trait Service {
    def mailboxGet(): IO[Throwable, Response[String]]

  }

  class JmapClient(configuration: JmapServerConfiguration) extends Jmap.Service {

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

object JmapSpec extends DefaultRunnableSpec {


  case class JmapServerConfiguration(imapPort: Int, jmapPort: Int, smtpPort: Int, url: String)

  private def createContainerTaskCyrus: GenericContainer = {
    val container = GenericContainer("cyrus-jmap").configure(configProvider => {
      configProvider.addExposedPort(143)
      configProvider.addExposedPort(80)
      configProvider.addExposedPort(25)
    })

    container.underlyingUnsafeContainer.withCreateContainerCmdModifier(cmd => cmd.withHostName("cyrus.domain"))
    container
  }

  private def createContainerTaskJames: GenericContainer = {
    val container = GenericContainer("linagora/james-memory:branch-master").configure(configProvider => {
      configProvider.addExposedPort(143)
      configProvider.addExposedPort(80)
      configProvider.addExposedPort(25)
    })

    container.underlyingUnsafeContainer.withCreateContainerCmdModifier(cmd => cmd.withHostName("cyrus.domain"))
    container
  }

  private def createInboxTask: RIO[GenericContainer, _] = RIO.fromFunction(container => {
    val imapClient = new IMAPClient()
    imapClient.connect(container.container.getHost, container.container.getMappedPort(143))
    imapClient.login("bob", "bob")
    //imapClient.create("INBOX")
  })


  val jmapClient: ZLayer[GenericContainer, TestFailure[Nothing], Has[Service]] = {
    val jmapServerConfig: ZManaged[GenericContainer, Throwable, JmapServerConfiguration] = ZManaged.make(ZIO.fromFunction[GenericContainer, Unit](container => container.start) *> createInboxTask *> ZIO.fromFunction[GenericContainer, JmapServerConfiguration](container => JmapServerConfiguration(
      container.container.getMappedPort(143),
      container.container.getMappedPort(80),
      container.container.getMappedPort(25),
      s"http://${container.container.getHost}:${container.container.getMappedPort(80)}/jmap/"
    ))
    )(_ => ZIO.fromFunction[GenericContainer, Unit](container => container.stop))

    (ZLayer.fromManaged(jmapServerConfig) >>> Jmap.live).mapError(TestFailure.die)
  }

  def spec = {
    val mailboxGet = testM[Service, Throwable]("Mailbox/get without parameter should list the user mailboxes") {
      for {
        response <- ZIO.fromFunctionM[Service, Throwable, Response[String]](jmapService => jmapService.mailboxGet())
        jmapResponse <- response.body.fromJson[JmapResponse] match {
          case Left(s) => ZIO.fail(new RuntimeException("error: " + s))
          case util.Right(value) => ZIO.apply(value)
        }
      } yield {
        println(response)
        println(jmapResponse)

        assert(jmapResponse.methodResponses(0).arguments.list.map(_.name))(contains(MailboxName("Inbox")))
      }
    }


    def createSuite(name: String, container: () => GenericContainer) = {
      suite(s"JmapSpec $name")(
        mailboxGet.provideLayer(jmapClient.map(_.get)).provide(container()),
        test("jepzofjep") {
          assert(true)(equalTo(false))
        } @@ ignore
      )
    }

    suite("jmap")(
      createSuite("cyrus", createContainerTaskCyrus _),
      createSuite("james", createContainerTaskJames _)
    )
  }
}
