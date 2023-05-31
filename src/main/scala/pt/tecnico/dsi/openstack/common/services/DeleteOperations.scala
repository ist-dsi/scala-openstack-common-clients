package pt.tecnico.dsi.openstack.common.services

import org.http4s.Header
import pt.tecnico.dsi.openstack.common.models.Identifiable

/**
 * Idempotent delete operations for a domain model of an Openstack REST API.
 * @define domainModel domainModel
 * @tparam F the effect to use.
 * @tparam Model the class of the domain model.
 */
trait DeleteOperations[F[_], Model <: Identifiable] { this: PartialCrudService[F] =>
  /**
   * Deletes the given $domainModel.
   * @param model the $domainModel to delete.
   * @param extraHeaders extra headers to pass when making the request. The `authToken` header is always added.
   */
  def delete(model: Model, extraHeaders: Header.ToRaw*): F[Unit] = delete(model.id, extraHeaders*)
  
  /**
   * Deletes the $domainModel with the given `id`.
   * @param id the id of the $domainModel to delete. Usually a random UUID.
   * @param extraHeaders extra headers to pass when making the request. The `authToken` header is always added.
   */
  def delete(id: String, extraHeaders: Header.ToRaw*): F[Unit] = delete(uri / id, extraHeaders*)
}