package pt.tecnico.dsi.openstack.common.services

import cats.effect.Sync
import cats.syntax.flatMap._
import fs2.Stream
import io.circe.{Decoder, Encoder}
import org.http4s.Method.POST
import org.http4s.Status.{Conflict, Successful}
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.{EntityDecoder, EntityEncoder, Header, Query, Uri}
import pt.tecnico.dsi.openstack.common.models.WithId

abstract class CrudService[F[_]: Sync: Client, Model: Decoder, Create: Encoder, Update: Encoder]
  (baseUri: Uri, val name: String, authToken: Header, wrapped: Boolean = true) extends Service[F](authToken) {

  import dsl._

  val pluralName = s"${name}s"
  val uri: Uri = baseUri / pluralName
  protected val wrappedAt: Option[String] = Option.when(wrapped)(name)

  def list(extraHeaders: Header*): Stream[F, WithId[Model]] = list(Query.empty, extraHeaders:_*)
  def list(query: Query, extraHeaders: Header*): Stream[F, WithId[Model]] = super.list[WithId[Model]](pluralName, uri, query, extraHeaders:_*)

  def create(value: Create, extraHeaders: Header*): F[WithId[Model]] = super.post(wrappedAt, value, uri, extraHeaders:_*)

  protected def createHandleConflict(value: Create, extraHeaders: Header*)(onConflict: F[WithId[Model]]): F[WithId[Model]] = {
    implicit val d: EntityDecoder[F, WithId[Model]] = unwrapped(wrappedAt)
    implicit val e: EntityEncoder[F, Create] = wrapped(wrappedAt)
    POST(value, uri, (authToken +: extraHeaders):_*).flatMap(client.run(_).use {
      case Successful(response) => response.as[WithId[Model]]
      case Conflict(_) => onConflict
      case response => F.raiseError(UnexpectedStatus(response.status))
    })
  }

  def get(id: String, extraHeaders: Header*): F[WithId[Model]] = super.get(wrappedAt, uri / id, extraHeaders:_*)

  def update(id: String, value: Update, extraHeaders: Header*): F[WithId[Model]] = super.patch(wrappedAt, value, uri / id, extraHeaders:_*)

  def delete(value: WithId[Model], extraHeaders: Header*): F[Unit] = delete(value.id, extraHeaders:_*)
  def delete(id: String, extraHeaders: Header*): F[Unit] = super.delete(uri / id, extraHeaders:_*)
}