package pt.tecnico.dsi.openstack.common.models

import org.http4s.{Method, Status, Uri}

final case class UnexpectedStatus(
  requestMethod: Method,
  requestUri: Uri,
  requestBody: String,
  responseStatus: Status,
  responseBody: String,
) extends RuntimeException:
  override def getMessage: String =
    val requestBodyText = if requestBody.nonEmpty then s" with body:\n$requestBody\n" else ""
    val responseBodyText = if responseBody.nonEmpty then s" with body:\n$responseBody\n" else ""
    s"""While executing $requestMethod $requestUri$requestBodyText got unexpected HTTP $responseStatus$responseBodyText""".stripMargin
