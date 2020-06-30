package pt.tecnico.dsi.openstack.common.models

import io.circe.Decoder
import io.circe.derivation.{deriveDecoder, renaming}

object Usage {
  implicit def decoder[T: Decoder]: Decoder[Usage[T]] = deriveDecoder(renaming.snakeCase)
}
case class Usage[T](inUse: T, limit: T, reserved: T, allocated: Option[T] = None)
