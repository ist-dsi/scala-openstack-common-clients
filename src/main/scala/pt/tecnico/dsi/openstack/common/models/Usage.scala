package pt.tecnico.dsi.openstack.common.models

import scala.annotation.nowarn
import cats.Show
import cats.implicits.showInterpolator
import io.circe.derivation.{deriveCodec, renaming}
import io.circe.{Codec, Decoder, Encoder}

object Usage {
  // nowarn because of a false negative from the compiler. The Encoder and Decoder is being inside the macro deriveCodec.
  @nowarn implicit def codec[T: Encoder: Decoder]: Codec[Usage[T]] = deriveCodec(renaming.snakeCase)
  implicit def show[T: Show]: Show[Usage[T]] = (usage: Usage[T]) =>
    show"${usage.inUse}/${usage.limit} in use (${usage.reserved} are reserved)"
}
final case class Usage[T](inUse: T, limit: T, reserved: T)
