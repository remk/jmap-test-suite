package jmap

import Jmap.{JmapServerConfiguration, Service}
import com.dimafeng.testcontainers.GenericContainer
import org.apache.commons.net.imap.IMAPClient
import sttp.client._
import zio.json._
import zio.test.Assertion._
import zio.test.TestAspect.ignore
import zio.test._
import zio.{test => _, _}

object JmapSpec extends DefaultRunnableSpec {

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

        assert(jmapResponse.methodResponses(0).asInstanceOf[MailboxGet].arguments.list.map(_.name))(contains(MailboxName("Inbox")))
      }
    }


    def createSuite(jmapServer: JMAPServer) = {
      suite(s"JmapSpec ${jmapServer.name}")(
        mailboxGet.provideLayer(jmapClient.map(_.get)).provide(jmapServer.getContainer()),
        test("jepzofjep") {
          assert(true)(equalTo(false))
        } @@ ignore
      )
    }

    suite("jmap")(
      createSuite(Cyrus),
      createSuite(James)
    )
  }
}
