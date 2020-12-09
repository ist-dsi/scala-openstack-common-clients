package pt.tecnico.dsi.openstack.common.services

import cats.effect.Sync
import io.circe.{Decoder, Encoder}
import org.http4s.client.Client
import org.http4s.{Header, Uri}
import pt.tecnico.dsi.openstack.common.models.Identifiable

abstract class CrudService[F[_]: Sync: Client, Model <: Identifiable, Create, Update]
  (baseUri: Uri, name: String, authToken: Header, wrapped: Boolean = true)
  (implicit val modelDecoder: Decoder[Model], val createEncoder: Encoder[Create], val updateEncoder: Encoder[Update])
  extends BaseCrudService[F](baseUri, name, authToken, wrapped)
    with CreateNonIdempotentOperations[F, Model, Create]
    with UpdateOperations[F, Model, Update]
    with CreateOperations[F, Model, Create, Update]
    with ListOperations[F, Model]
    with ReadOperations[F, Model]
    with DeleteOperations[F, Model]
