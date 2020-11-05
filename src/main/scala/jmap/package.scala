import zio.json.ast.Json
import zio.json.{DeriveJsonDecoder, JsonDecoder}
import zio.json.ast.Json.Obj

/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ************************************************************** */
package object jmap {

  sealed trait Invocation
  case class MailboxGet(methodName: MethodName, arguments: MailboxGetResponse, clientId: ClientId) extends Invocation
  case class MailboxQuery(methodName: MethodName, arguments: MailboxQueryResponse, clientId: ClientId) extends Invocation

  object Invocation {
    implicit val decoder: JsonDecoder[Invocation] =
      JsonDecoder[(MethodName, Obj, ClientId)].map {
        case (MethodName.mailboxGet, arguments, clientId) => MailboxGet(MethodName.mailboxGet,
          //TODO unsafe and ugly
          MailboxGetResponse.decoder.decodeJson(Json.encoder.encodeJson(arguments, None)).right.get,
          clientId)
        case (MethodName.mailboxQuery, arguments, clientId) => MailboxQuery(MethodName.mailboxQuery,
          //TODO unsafe and ugly
          MailboxQueryResponse.decoder.decodeJson(Json.encoder.encodeJson(arguments, None)).right.get,
          clientId)

      }
  }


  case class JmapResponse(methodResponses: List[Invocation], sessionState: SessionState)
  object JmapResponse {
    implicit val decoder: JsonDecoder[JmapResponse] = DeriveJsonDecoder.gen[JmapResponse]
  }
}
