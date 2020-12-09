package pt.tecnico.dsi.openstack.common.models

import cats.{Show, derived}
import io.circe.Decoder
import io.circe.derivation.{deriveDecoder, renaming}

object Usage {
  implicit def decoder[T: Decoder]: Decoder[Usage[T]] = deriveDecoder(renaming.snakeCase)
  implicit def show[T: Show]: Show[Usage[T]] = derived.semiauto.show
}
final case class Usage[T](inUse: T, limit: T, reserved: T, allocated: Option[T] = None)
