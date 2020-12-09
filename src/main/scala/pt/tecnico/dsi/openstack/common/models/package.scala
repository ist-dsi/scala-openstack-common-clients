package pt.tecnico.dsi.openstack.common

import cats.Show
import org.http4s.Uri

package object models {
  // https://github.com/http4s/http4s/issues/3998
  implicit val showUri: Show[Uri] = Show.fromToString[Uri]
}
