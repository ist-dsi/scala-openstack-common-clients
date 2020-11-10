package pt.tecnico.dsi.openstack.common.services

import cats.effect.Sync
import cats.syntax.flatMap._
import fs2.Stream
import io.circe.{Decoder, Encoder}
import org.http4s.client.Client
import org.http4s.{Header, Query, Uri}
import pt.tecnico.dsi.openstack.common.models.Identifiable

abstract class CrudService[F[_]: Sync: Client, Model <: Identifiable, Create, Update]
  (baseUri: Uri, val name: String, authToken: Header, wrapped: Boolean = true)
  (implicit val modelDecoder: Decoder[Model], val createEncoder: Encoder[Create], val updateEncoder: Encoder[Update])
  extends Service[F](authToken) {
  
  val pluralName = s"${name}s"
  val uri: Uri = baseUri / pluralName
  protected val wrappedAt: Option[String] = Option.when(wrapped)(name)
  
  def stream(pairs: (String, String)*): Stream[F, Model] = stream(Query.fromPairs(pairs:_*))
  def stream(query: Query, extraHeaders: Header*): Stream[F, Model] = super.stream[Model](pluralName, uri.copy(query = query), extraHeaders:_*)
  
  def list(pairs: (String, String)*): F[List[Model]] = list(Query.fromPairs(pairs:_*))
  def list(query: Query, extraHeaders: Header*): F[List[Model]] = super.list[Model](pluralName, uri.copy(query = query), extraHeaders:_*)
  
  def create(create: Create, extraHeaders: Header*): F[Model] = super.post(wrappedAt, create, uri, extraHeaders:_*)
  
  /**
   * Default implementation to resolve the conflict that arises when implementing the createOrUpdate.
   * In other words implements the idempotency logic of the create.
   * @param existing the existing model
   * @param create the create model
   * @param keepExistingElements whether to keep existing elements. See `createOrUpdate` for a more detailed explanation.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   * @return the existing model if no modifications are required, otherwise the updated model.
   */
  def defaultResolveConflict(existing: Model, create: Create, keepExistingElements: Boolean, extraHeaders: Seq[Header]): F[Model]
  
  /**
   * An idempotent create. If the model that is to be created already exists then it will be updated, or simply returned if no modifications
   * are necessary. The definition on what is considered already existing is left to the implementation as it is specific to the `Model`
   * in question.
   *
   * This function simply calls the overloaded version passing in `keepExistingElements` set to `true`.
   *
   * @param create the create settings.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   * @return the created model, or if it already exists the existing or updated model.
   */
  def createOrUpdate(create: Create, extraHeaders: Header*): F[Model] =
    createOrUpdate(create, keepExistingElements = true, extraHeaders:_*)
  /**
   * An idempotent create. If the model that is to be created already exists then it will be updated, or simply returned if no modifications
   * are necessary. The definition on what is considered already existing is left to the implementation as it is specific to the `Model`
   * in question. [[defaultResolveConflict]] will be invoked to resolve the conflict that will arise when the create is being performed
   * on a model that already exists.
   *
   * @param create the create settings.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   * @param keepExistingElements the create operation can be interpreted with two possible meanings:
   *
   *   1. Create a `Model` with the settings in `Create`. Extra settings that may already exist (when the model is being updated)
   *      will be preserved as much as possible.
   *   1. Create a `Model` with <b>exactly</b> the settings in `Create`. Extra settings that may already exist are removed.
   *      This is done in a best effort approach, since its not achievable in the general case, as some settings are not updatable
   *      after creating the Model (the create and the update are asymmetric).
   *
   *   Setting `keepExistingElements` to `true` will follow the logic in point 1.
   *   Setting it to `false` will follow point 2, thus making the update more stringent.
   * @return the created model, or if it already exists the existing or updated model.
   */
  def createOrUpdate(create: Create, keepExistingElements: Boolean, extraHeaders: Header*): F[Model] =
    createOrUpdate(create, keepExistingElements, extraHeaders)()
  /**
   * An idempotent create. If the model that is to be created already exists then it will be updated, or simply returned if no modifications
   * are necessary. The definition on what is considered already existing is left to the implementation as it is specific to the `Model`
   * in question.
   * @param create the create settings.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   * @param keepExistingElements the create operation can be interpreted with two possible meanings:
   *
   *   1. Create a `Model` with the settings in `Create`. Extra settings that may already exist (when the model is being updated)
   *      will be preserved as much as possible.
   *   1. Create a `Model` with <b>exactly</b> the settings in `Create`. Extra settings that may already exist are removed.
   *      This is done in a best effort approach, since its not achievable in the general case, as some settings are not updatable
   *      after creating the Model (the create and the update are asymmetric).
   *
   *   Setting `keepExistingElements` to `true` will follow the logic in point 1.
   *   Setting it to `false` will follow the logic in point 2, thus making the update more stringent.
   *   In most cases removing extra settings will break things, so this flag is set to `true` by default.
   * @param resolveConflict the function used to resolve the conflict that will arise when the create is being performed
   *                        on a model that already exists. By default it just invokes [[defaultResolveConflict]].
   * @return the created model, or if it already exists the existing or updated model.
   */
  def createOrUpdate(create: Create, keepExistingElements: Boolean = true, extraHeaders: Seq[Header] = Seq.empty)
    (resolveConflict: (Model, Create) => F[Model] = defaultResolveConflict(_, _, keepExistingElements, extraHeaders)): F[Model]
  
  protected def createHandleConflict(create: Create, uri: Uri, extraHeaders: Seq[Header])(onConflict: F[Model]): F[Model] =
    super.postHandleConflict[Create, Model](wrappedAt, create, uri, extraHeaders)(onConflict)
  
  protected def createHandleConflictWithError[E <: Throwable: Decoder](create: Create, uri: Uri, extraHeaders: Seq[Header])
    (onConflict: E /=> F[Model]): F[Model] =
    super.postHandleConflictWithError[Create, Model, E](wrappedAt, create, uri, extraHeaders)(onConflict)
  
  def get(id: String, extraHeaders: Header*): F[Option[Model]] = super.getOption(wrappedAt, uri / id, extraHeaders:_*)
  def apply(id: String, extraHeaders: Header*): F[Model] =
    get(id, extraHeaders:_*).flatMap {
      case Some(model) => F.pure(model)
      case None => F.raiseError(new NoSuchElementException(s"""Could not find $name with id "$id"."""))
    }
  
  def update(id: String, update: Update, extraHeaders: Header*): F[Model] =
    super.put(wrappedAt, update, uri / id, extraHeaders:_*)
  
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