package pt.tecnico.dsi.openstack.common.services

import io.circe.{Decoder, Encoder}
import org.http4s.Header

/**
 * Idempotent update operations for a domain model of an Openstack REST API.
 * @define domainModel domainModel
 * @tparam F the effect to use.
 * @tparam Model the class of the domain model.
 * @tparam Update the class with the available update elements of $domainModel.
 */
trait UpdateOperations[F[_], Model, Update] { this: PartialCrudService[F] =>
  given modelDecoder: Decoder[Model]
  given updateEncoder: Encoder[Update]
  
  /**
   * Updates the $domainModel with the given `id` using the values in `update`.
   * @param id the id of the $domainModel to update.
   * @param update the values to use in the update.
   * @param extraHeaders extra headers to pass when making the request. The `authToken` header is always added.
   * @return the updated $domainModel.
   */
  def update(id: String, update: Update, extraHeaders: Header.ToRaw*): F[Model] = put(wrappedAt, update, uri / id, extraHeaders*)
  
  /*
   * For domain classes which use the .Create for the .Update class this might be misleading
   *    nova.quotas(project.id, Quota.Create(...)) // This is an update
   *    nova.quotas(Quota.Create(...)) // This is a create
   */
  /**
   * Allows a simpler syntax to update ${domainModel}(s).
   * For example instead of:
   * {{{
   *    cinder.quotas.update(project.id, Quota.Update(...))
   * }}}
   * We can do:
   * {{{
   *    cinder.quotas(project.id, Quota.Update(...))
   * }}}
   */
  def apply(id: String, update: Update, extraHeaders: Header.ToRaw*): F[Model] = this.update(id, update, extraHeaders*)
}