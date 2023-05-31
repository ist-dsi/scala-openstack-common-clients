package pt.tecnico.dsi.openstack.common.services

import cats.syntax.flatMap.*
import io.circe.Decoder
import org.http4s.Header

/**
 * Idempotent read operations for a domain model of an Openstack REST API.
 * @define domainModel domainModel
 * @tparam F the effect to use.
 * @tparam Model the class of the domain model.
 */
trait ReadOperations[F[_], Model] { this: PartialCrudService[F] =>
  given modelDecoder: Decoder[Model]
  
  /**
   * Gets the $domainModel with the specified `id`.
   * @param id the id of the $domainModel to get. Usually a random UUID.
   * @param extraHeaders extra headers to pass when making the request. The `authToken` header is always added.
   * @return a Some with the $domainModel if it exists. A None otherwise.
   */
  def get(id: String, extraHeaders: Header.ToRaw*): F[Option[Model]] = getOption(wrappedAt, uri / id, extraHeaders*)
  
  /**
   * Gets the $domainModel with the specified `id`, assuming it exists.
   * @param id the id of the $domainModel to get. Usually a random UUID.
   * @param extraHeaders extra headers to pass when making the request. The `authToken` header is always added.
   * @return the $domainModel with the given `id`. If none exists F will contain an error.
   */
  def apply(id: String, extraHeaders: Header.ToRaw*): F[Model] =
    get(id, extraHeaders*).flatMap:
      case Some(model) => F.pure(model)
      case None => F.raiseError(new NoSuchElementException(s"""Could not find ${(this: Service[F]).name} with id "$id"."""))
}