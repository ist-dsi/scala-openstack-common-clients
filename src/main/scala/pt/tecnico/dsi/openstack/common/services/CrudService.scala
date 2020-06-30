package pt.tecnico.dsi.openstack.common.services

import cats.effect.Sync
import cats.syntax.flatMap._
import fs2.Stream
import io.circe.{Decoder, Encoder}
import org.http4s.Method.POST
import org.http4s.Status.{Conflict, Successful}
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.{EntityDecoder, EntityEncoder, Header, Query, Response, Uri}
import pt.tecnico.dsi.openstack.common.models.WithId

abstract class CrudService[F[_]: Sync: Client, Model: Decoder, Create: Encoder, Update: Encoder](baseUri: Uri, val name: String, authToken: Header)
  extends Service[F](authToken) {

  import dsl._

  val pluralName = s"${name}s"
  val uri: Uri = baseUri / pluralName

  def list(): Stream[F, WithId[Model]] = list(Query.empty)
  def list(query: Query): Stream[F, WithId[Model]] = super.list[WithId[Model]](pluralName, uri, query)

  def create(value: Create): F[WithId[Model]] = super.post(value, uri, wrappedAt = Some(name))

  protected def createHandleConflict(value: Create)(onConflict: Response[F] => F[WithId[Model]]): F[WithId[Model]] = {
    implicit val d: EntityDecoder[F, WithId[Model]] = unwrapped(Some(name))
    implicit val e: EntityEncoder[F, Create] = wrapped(Some(name))
    POST(value, uri, authToken).flatMap(client.run(_).use {
      case Successful(response) => response.as[WithId[Model]]
      case Conflict(response) => onConflict(response)
      case response => F.raiseError(UnexpectedStatus(response.status))
    })
  }

  def get(id: String): F[WithId[Model]] = super.get(uri / id, wrappedAt = Some(name))

  def update(id: String, value: Update): F[WithId[Model]] = super.patch(value, uri / id, wrappedAt = Some(name))

  def delete(value: WithId[Model]): F[Unit] = delete(value.id)
  def delete(id: String): F[Unit] = super.delete(uri / id)
}