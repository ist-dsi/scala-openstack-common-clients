package pt.tecnico.dsi.openstack.common.services

import io.circe.{Decoder, Encoder}
import org.http4s.Header

trait CreateNonIdempotentOperations[F[_], Model, Create] { this: BaseCrudService[F] =>
  implicit val modelDecoder: Decoder[Model]
  implicit val createEncoder: Encoder[Create]
  
  def create(create: Create, extraHeaders: Header*): F[Model] = post(wrappedAt, create, uri, extraHeaders:_*)
  
  /*
   * We could go even further an implement in the client:
   *    keystone(Project.Create(name, domainId = Some(usersDomain.id)))
   * That would require the apply method on the client to have a path dependent type on the return
   */
  /**
   * Allows a simpler syntax to create domain objects.
   * For example instead of:
   * {{{
   *    keystone.projects.create(Project.Create(name, domainId = Some(usersDomain.id)))
   * }}}
   * We can do:
   * {{{
   *    keystone.projects(Project.Create(name, domainId = Some(usersDomain.id)))
   * }}}
   */
  def apply(create: Create, extraHeaders: Header*): F[Model] = this.create(create, extraHeaders:_*)
}