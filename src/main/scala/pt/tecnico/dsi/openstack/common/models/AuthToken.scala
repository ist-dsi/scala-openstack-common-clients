package pt.tecnico.dsi.openstack.common.models

import org.http4s.{Header, ParseResult}
import org.typelevel.ci.CIString

object AuthToken {
  implicit val headerInstance: Header[AuthToken, Header.Single] =
    Header.create(CIString("X-Auth-Token"), _.token, s => ParseResult.success(AuthToken(s)))
}
case class AuthToken(token: String)
