package pt.tecnico.dsi.openstack.common.services

import io.circe.Decoder
import org.http4s.{Header, Uri}

trait CreateOperations[F[_], Model, Create, Update] {
  this: BaseCrudService[F] with CreateNonIdempotentOperations[F, Model, Create] with UpdateOperations[F, Model, Update] =>
  
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
    postHandleConflict[Create, Model](wrappedAt, create, uri, extraHeaders)(onConflict)
  
  protected def createHandleConflictWithError[E <: Throwable: Decoder](create: Create, uri: Uri, extraHeaders: Seq[Header])
    (onConflict: E /=> F[Model]): F[Model] =
    postHandleConflictWithError[Create, Model, E](wrappedAt, create, uri, extraHeaders)(onConflict)
}