package pt.tecnico.dsi.openstack.common.models

import cats.Show
import cats.implicits.toShow
import io.circe.Decoder
import io.circe.derivation.{deriveDecoder, renaming}

object Usage {
  implicit def decoder[T: Decoder]: Decoder[Usage[T]] = deriveDecoder(renaming.snakeCase)
  // The semiautomatic derivation is failing. Can't understand why
  implicit def show[T: Show]: Show[Usage[T]] = Show.show { usage =>
    s"Usage(inUse = ${usage.inUse.show}, limit = ${usage.limit.show}, reserved = ${usage.reserved.show}, allocated = ${usage.allocated.show})"
  }
}
final case class Usage[T](inUse: T, limit: T, reserved: T, allocated: Option[T] = None)
