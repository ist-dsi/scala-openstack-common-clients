package pt.tecnico.dsi.openstack.common.models

import cats.Show
import cats.implicits.showInterpolator
import io.circe.Decoder
import io.circe.derivation.{deriveDecoder, renaming}

object Usage {
  implicit def decoder[T: Decoder]: Decoder[Usage[T]] = deriveDecoder(renaming.snakeCase)
  implicit def show[T: Show]: Show[Usage[T]] = (usage: Usage[T]) =>
    show"${usage.inUse} of ${usage.limit} in use. ${usage.reserved} are reserved."
}
final case class Usage[T](inUse: T, limit: T, reserved: T)
