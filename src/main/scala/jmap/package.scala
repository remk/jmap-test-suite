import zio.json.ast.Json
import zio.json.{DeriveJsonDecoder, JsonDecoder}
import zio.json.ast.Json.Obj

package object jmap {

  sealed trait Invocation
  case class MailboxGet(methodName: MethodName, arguments: MailboxGetResponse, clientId: ClientId) extends Invocation
  case class MailboxQuery(methodName: MethodName, arguments: MailboxQueryResponse, clientId: ClientId) extends Invocation

  object Invocation {
    implicit val decoder: JsonDecoder[Invocation] =
      JsonDecoder[(MethodName, Obj, ClientId)].mapOrFail {
        case (MethodName.mailboxGet, arguments, clientId) => MailboxGetResponse.decoder.decodeJson(Json.encoder.encodeJson(arguments, None))
          .map( MailboxGet(MethodName.mailboxGet, _, clientId))
        case (MethodName.mailboxQuery, arguments, clientId) => MailboxQueryResponse.decoder.decodeJson(Json.encoder.encodeJson(arguments, None))
          .map( MailboxQuery(MethodName.mailboxQuery, _, clientId))
      }
  }

  case class JmapResponse(methodResponses: List[Invocation], sessionState: SessionState)
  object JmapResponse {
    implicit val decoder: JsonDecoder[JmapResponse] = DeriveJsonDecoder.gen[JmapResponse]
  }
}
