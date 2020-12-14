package pt.tecnico.dsi.openstack.common.models

import org.http4s.{Method, Status, Uri}

final case class UnexpectedStatus(requestMethod: Method, requestUri: Uri, requestBody: String, responseStatus: Status, responseBody: String)
  extends RuntimeException {
  override def getMessage: String =
    s"""While executing $requestMethod $requestUri with body:
       |$responseBody
       |Got unexpected HTTP $responseStatus with body:
       |$responseBody"""".stripMargin
}