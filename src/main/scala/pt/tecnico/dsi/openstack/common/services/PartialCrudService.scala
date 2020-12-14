package pt.tecnico.dsi.openstack.common.services

import cats.effect.Sync
import org.http4s.client.Client
import org.http4s.{Header, Uri}

/**
 * A crud service which does not implement all the CRUD operations.
 * @define domainModel domainModel
 * @param baseUri the base uri of the Openstack Service API.
 * @param name the name of the domain class this BaseCrudService handles.
 * @param authToken the authentication token used to authorize against Openstack.
 * @param wrapped whether the request and response bodies should be wrapped inside a JsonObject.
 * @tparam F the effect to use.
 */
abstract class PartialCrudService[F[_]: Sync: Client](baseUri: Uri, name: String, authToken: Header, wrapped: Boolean = true)
  extends Service[F](baseUri, name, authToken) {
  protected val wrappedAt: Option[String] = Option.when(wrapped)(name)
}