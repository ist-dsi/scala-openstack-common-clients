package pt.tecnico.dsi.openstack.common.services

import io.circe.{Decoder, Encoder}
import org.http4s.Header

trait UpdateOperations[F[_], Model, Update] { this: BaseCrudService[F] =>
  implicit val modelDecoder: Decoder[Model]
  implicit val updateEncoder: Encoder[Update]
  
  def update(id: String, update: Update, extraHeaders: Header*): F[Model] = put(wrappedAt, update, uri / id, extraHeaders:_*)
  
  /*
   * For domain classes which use the .Create for the .Update class this might be misleading
   *    nova.quotas(project.id, Quota.Create(...)) // This is an update
   *    nova.quotas(Quota.Create(...)) // This is a create
   */
  /**
   * Allows a simpler syntax to update domain objects.
   * For example instead of:
   * {{{
   *    cinder.quotas.update(project.id, Quota.Update(...))
   * }}}
   * We can do:
   * {{{
   *    cinder.quotas(project.id, Quota.Update(...))
   * }}}
   */
  def apply(id: String, update: Update, extraHeaders: Header*): F[Model] = this.update(id, update, extraHeaders:_*)
}