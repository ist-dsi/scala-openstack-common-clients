package pt.tecnico.dsi.openstack.common.services

import cats.effect.Sync
import fs2.Stream
import io.circe.{Decoder, Encoder}
import org.http4s.client.Client
import org.http4s.{Header, Query, Uri}
import pt.tecnico.dsi.openstack.common.models.Identifiable

abstract class CrudService[F[_]: Sync: Client, Model <: Identifiable: Decoder, Create: Encoder, Update: Encoder]
  (baseUri: Uri, val name: String, authToken: Header, wrapped: Boolean = true) extends Service[F](authToken) {

  val pluralName = s"${name}s"
  val uri: Uri = baseUri / pluralName
  protected val wrappedAt: Option[String] = Option.when(wrapped)(name)

  def list(extraHeaders: Header*): Stream[F, Model] = list(Query.empty, extraHeaders:_*)
  def list(query: Query, extraHeaders: Header*): Stream[F, Model] = super.list[Model](pluralName, uri, query, extraHeaders:_*)

  def create(value: Create, extraHeaders: Header*): F[Model] = super.post(wrappedAt, value, uri, extraHeaders:_*)

  protected def createHandleConflict(value: Create, extraHeaders: Header*)(onConflict: F[Model]): F[Model] =
    super.postHandleConflict(wrappedAt, value, uri, extraHeaders:_*)(onConflict)

  def get(id: String, extraHeaders: Header*): F[Option[Model]] = super.getOption(wrappedAt, uri / id, extraHeaders:_*)
  def apply(id: String, extraHeaders: Header*): F[Model] = super.get(wrappedAt, uri / id, extraHeaders:_*)

  def update(id: String, value: Update, extraHeaders: Header*): F[Model] = super.patch(wrappedAt, value, uri / id, extraHeaders:_*)

  def delete(value: Model, extraHeaders: Header*): F[Unit] = delete(value.id, extraHeaders:_*)
  def delete(id: String, extraHeaders: Header*): F[Unit] = super.delete(uri / id, extraHeaders:_*)
}