package pt.tecnico.dsi.openstack.common.services

import cats.effect.Sync
import org.http4s.client.Client
import org.http4s.{Header, Uri}

abstract class BaseCrudService[F[_]: Sync: Client](baseUri: Uri, name: String, authToken: Header, wrapped: Boolean = true)
  extends Service[F](baseUri, name, authToken) {
  protected val wrappedAt: Option[String] = Option.when(wrapped)(name)
}