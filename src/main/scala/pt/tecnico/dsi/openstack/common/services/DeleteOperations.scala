package pt.tecnico.dsi.openstack.common.services

import org.http4s.Header
import pt.tecnico.dsi.openstack.common.models.Identifiable

trait DeleteOperations[F[_], Model <: Identifiable] { this: BaseCrudService[F] =>
  def delete(model: Model, extraHeaders: Header*): F[Unit] = delete(model.id, extraHeaders:_*)
  def delete(id: String, extraHeaders: Header*): F[Unit] = delete(uri / id, extraHeaders:_*)
}