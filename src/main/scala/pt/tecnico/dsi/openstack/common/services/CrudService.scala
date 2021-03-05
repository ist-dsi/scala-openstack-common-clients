package pt.tecnico.dsi.openstack.common.services

import cats.effect.Concurrent
import io.circe.{Decoder, Encoder}
import org.http4s.Uri
import org.http4s.client.Client
import pt.tecnico.dsi.openstack.common.models.{Identifiable, AuthToken}

/**
 * A complete CRUD service capable of executing create, read, list, update, and delete operations idempotently.
 * @define domainModel domainModel
 * @param baseUri the base uri of the Openstack Service API.
 * @param name the name of the domain class this CrudService handles.
 * @param authToken the authentication token used to authorize against Openstack.
 * @param wrapped whether the request and response bodies should be wrapped inside a JsonObject.
 * @param modelDecoder a decoder capable of decoding the json returned in the responses to a `Model`.
 * @param createEncoder an encoder capable of producing a json from an instance of `Create`.
 * @param updateEncoder an encoder capable of producing a json from an instance of `Update`.
 * @tparam F the effect to use.
 * @tparam Model the class of the domain model.
 * @tparam Create the class with the available create elements of $domainModel.
 * @tparam Update the class with the available update elements of $domainModel.
 */
abstract class CrudService[F[_]: Concurrent: Client, Model <: Identifiable, Create, Update]
  (baseUri: Uri, name: String, authToken: AuthToken, wrapped: Boolean = true)
  (implicit val modelDecoder: Decoder[Model], val createEncoder: Encoder[Create], val updateEncoder: Encoder[Update])
  extends PartialCrudService[F](baseUri, name, authToken, wrapped)
    with CreateNonIdempotentOperations[F, Model, Create]
    with UpdateOperations[F, Model, Update]
    with CreateOperations[F, Model, Create, Update]
    with ListOperations[F, Model]
    with ReadOperations[F, Model]
    with DeleteOperations[F, Model]
