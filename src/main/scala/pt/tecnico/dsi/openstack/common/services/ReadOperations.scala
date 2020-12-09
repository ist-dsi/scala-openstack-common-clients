package pt.tecnico.dsi.openstack.common.services

import cats.syntax.flatMap._
import io.circe.Decoder
import org.http4s.Header

trait ReadOperations[F[_], Model] { this: BaseCrudService[F] =>
  implicit val modelDecoder: Decoder[Model]
  
  def get(id: String, extraHeaders: Header*): F[Option[Model]] = getOption(wrappedAt, uri / id, extraHeaders:_*)
  def apply(id: String, extraHeaders: Header*): F[Model] =
    get(id, extraHeaders:_*).flatMap {
      case Some(model) => F.pure(model)
      case None => F.raiseError(new NoSuchElementException(s"""Could not find $name with id "$id"."""))
    }
}