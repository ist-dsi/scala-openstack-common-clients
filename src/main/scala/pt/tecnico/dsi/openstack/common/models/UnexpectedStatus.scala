package pt.tecnico.dsi.openstack.common.models

import scala.util.control.NoStackTrace
import org.http4s.Status

final case class UnexpectedStatus(status: Status, body: String) extends RuntimeException with NoStackTrace {
  override def getMessage: String = s"unexpected HTTP $status:\n$body"
}