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

  def create(create: Create, extraHeaders: Header*): F[Model] = super.post(wrappedAt, create, uri, extraHeaders:_*)

  protected def createHandleConflict(create: Create, extraHeaders: Header*)(onConflict: F[Model]): F[Model] =
    super.postHandleConflict(wrappedAt, create, uri, extraHeaders:_*)(onConflict)

  def get(id: String, extraHeaders: Header*): F[Option[Model]] = super.getOption(wrappedAt, uri / id, extraHeaders:_*)
  def apply(id: String, extraHeaders: Header*): F[Model] = super.get(wrappedAt, uri / id, extraHeaders:_*)

  def update(id: String, update: Update, extraHeaders: Header*): F[Model] = super.patch(wrappedAt, update, uri / id, extraHeaders:_*)

  def delete(model: Model, extraHeaders: Header*): F[Unit] = delete(model.id, extraHeaders:_*)
  def delete(id: String, extraHeaders: Header*): F[Unit] = super.delete(uri / id, extraHeaders:_*)
  
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
  
  /*
   * For domain classes which use the .Create for the .Update class this might be misleading
   *    nova.quotas(project.id, Quota.Create(...)) // This is an update
   *    nova.quotas(Quota.Create(...)) // This is a create
   */
  /**
   * Allows a simpler syntax to update domain objects.
   * For example instead of:
   * {{{
   *    cinder.quotas.update(project.id, Quota.Update(...))
   * }}}
   * We can do:
   * {{{
   *    cinder.quotas(project.id, Quota.Update(...))
   * }}}
   */
  def apply(id: String, update: Update, extraHeaders: Header*): F[Model] = this.update(id, update, extraHeaders:_*)
}