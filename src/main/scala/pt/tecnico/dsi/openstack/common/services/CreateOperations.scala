package pt.tecnico.dsi.openstack.common.services

import io.circe.Decoder
import org.http4s.{Header, Uri}

/**
 * Idempotent create operations for a domain model of an Openstack REST API.
 * @define domainModel domainModel
 * @tparam F the effect to use.
 * @tparam Model the class of the domain model.
 * @tparam Create the class with the available create elements of $domainModel.
 * @tparam Update the class with the available update elements of $domainModel.
 */
trait CreateOperations[F[_], Model, Create, Update] {
  this: PartialCrudService[F] & CreateNonIdempotentOperations[F, Model, Create] & UpdateOperations[F, Model, Update] =>
  
  /**
   * Default implementation to resolve the conflict that arises when implementing the createOrUpdate.
   * In other words implements the idempotency logic of the create.
   * @param existing the existing $domainModel.
   * @param create the values to use in the create.
   * @param keepExistingElements whether to keep existing elements. See `createOrUpdate` for a more detailed explanation.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   * @return the existing model if no modifications are required, otherwise the updated model.
   */
  def defaultResolveConflict(existing: Model, create: Create, keepExistingElements: Boolean, extraHeaders: Seq[Header.ToRaw]): F[Model]
  
  /**
   * An idempotent create. If the model that is to be created already exists then it will be updated, or simply returned if no modifications
   * are necessary. The definition on what is considered already existing is left to the implementation as it is specific to the `Model`
   * in question.
   *
   * This function simply calls the overloaded version passing in `keepExistingElements` set to `true`.
   *
   * @param create the values to use in the create.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   * @return the created $domainModel, or if it already exists the existing or updated model.
   */
  def createOrUpdate(create: Create, extraHeaders: Header.ToRaw*): F[Model] =
    createOrUpdate(create, keepExistingElements = true, extraHeaders*)
  /**
   * An idempotent create. If the model that is to be created already exists then it will be updated, or simply returned if no modifications
   * are necessary. The definition on what is considered already existing is left to the implementation as it is specific to the `Model`
   * in question. [[defaultResolveConflict]] will be invoked to resolve the conflict that will arise when the create is being performed
   * on a model that already exists.
   *
   * @param create the values to use in the create.
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
   * @return the created $domainModel, or if it already exists the existing or updated model.
   */
  def createOrUpdate(create: Create, keepExistingElements: Boolean, extraHeaders: Header.ToRaw*): F[Model] =
    createOrUpdate(create, keepExistingElements, extraHeaders)()
  /**
   * An idempotent create. If the model that is to be created already exists then it will be updated, or simply returned if no modifications
   * are necessary. The definition on what is considered already existing is left to the implementation as it is specific to the `Model`
   * in question.
   * @param create the values to use in the create.
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
   * @return the created $domainModel, or if it already exists the existing or updated model.
   */
  def createOrUpdate(create: Create, keepExistingElements: Boolean = true, extraHeaders: Seq[Header.ToRaw] = Seq.empty)
    (resolveConflict: (Model, Create) => F[Model] = defaultResolveConflict(_, _, keepExistingElements, extraHeaders)): F[Model]
  
  /**
   * Creates a new $domainModel with the given `create` values using `uri`. If a Conflict is returned execute `onConflict` to resolve it.
   * @param create the values to use in the create.
   * @param uri the uri to which the request will be made.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   * @param onConflict the computation returning a $domainModel to execute when a Conflict is returned in the create.
   * @return the created $domainModel, or the updated $domainModel by `onConflict`.
   */
  protected def createHandleConflict(create: Create, uri: Uri, extraHeaders: Seq[Header.ToRaw])(onConflict: F[Model]): F[Model] =
    postHandleConflict[Create, Model](wrappedAt, create, uri, extraHeaders)(onConflict)
  
  /**
   * Creates a new $domainModel with the given `create` values using `uri`. If the create is not successful the response will be parsed to
   * an `E` and the `onConflict` partial function will be called. If the function is defined for the given `E` the computation returned
   * by it will be used to resolve the conflict. If the function is not defined `F` will contain the parsed `E`.
   * @param create the values to use in the create.
   * @param uri the uri to which the request will be made.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   * @param onConflict the computation returning a $domainModel to execute when a Conflict is returned in the create.
   * @tparam E
   * @return the created $domainModel, or the updated $domainModel by `onConflict`.
   */
  protected def createHandleConflictWithError[E <: Throwable: Decoder](create: Create, uri: Uri, extraHeaders: Seq[Header.ToRaw])
    (onConflict: E /=> F[Model]): F[Model] =
    postHandleConflictWithError[Create, Model, E](wrappedAt, create, uri, extraHeaders)(onConflict)
}