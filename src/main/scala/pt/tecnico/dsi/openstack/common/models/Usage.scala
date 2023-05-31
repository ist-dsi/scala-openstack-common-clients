package pt.tecnico.dsi.openstack.common.models

import cats.Show
import cats.implicits.showInterpolator
import io.circe.derivation.ConfiguredCodec
import io.circe.{Codec, Decoder, Encoder}

object Usage:
  given codec[T: Encoder: Decoder]: Codec[Usage[T]] = ConfiguredCodec.derived
  given show[T: Show]: Show[Usage[T]] = (usage: Usage[T]) =>
    show"${usage.inUse}/${usage.limit} in use (${usage.reserved} are reserved)"
final case class Usage[T](inUse: T, limit: T, reserved: T)
