package pt.tecnico.dsi.openstack.common

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
import cats.Show
import org.http4s.Uri

package object models {
  implicit val showUri: Show[Uri] = Show.fromToString[Uri]
  implicit val showOffsetDateTime: Show[OffsetDateTime] = Show.show(_.format(ISO_OFFSET_DATE_TIME))
}
