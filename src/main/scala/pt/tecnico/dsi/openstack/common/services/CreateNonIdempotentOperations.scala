package pt.tecnico.dsi.openstack.common.services

import io.circe.{Decoder, Encoder}
import org.http4s.Header

/**
 * Non-idempotent create operations for a domain model of an Openstack REST API.
 * @define domainModel domainModel
 * @tparam F the effect to use.
 * @tparam Model the class of the domain model.
 * @tparam Create the class with the available create elements of $domainModel.
 */
trait CreateNonIdempotentOperations[F[_], Model, Create] { this: PartialCrudService[F] =>
  implicit val modelDecoder: Decoder[Model]
  implicit val createEncoder: Encoder[Create]
  
  /**
   * Creates a new $domainModel with the given `create` values.
   * @param create the values to use in the create.
   * @param extraHeaders extra headers to pass when making the request. The `authToken` header is always added.
   * @return the created $domainModel
   */
  def create(create: Create, extraHeaders: Header*): F[Model] = post(wrappedAt, create, uri, extraHeaders:_*)
  
  /*
   * We could go even further an implement in the client:
   *    keystone(Project.Create(name, domainId = Some(usersDomain.id)))
   * That would require the apply method on the client to have a path dependent type on the return
   */
  /**
   * Allows a simpler syntax to create ${domainModel}(s).
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