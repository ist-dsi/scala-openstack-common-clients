package pt.tecnico.dsi.openstack.common.services

import fs2.Stream
import io.circe.Decoder
import org.http4s.{Header, Query}

/**
 * List operations for a domain model of an Openstack REST API.
 * @define domainModel domainModel
 * @tparam F the effect to use.
 * @tparam Model the class of the domain model.
 */
trait ListOperations[F[_], Model] { this: PartialCrudService[F] =>
  implicit val modelDecoder: Decoder[Model]
  
  /**
   * Streams ${domainModel}(s) using the specified `query`. Automatically fetches more pages if more elements of the stream are consumed.
   * @param pairs the pairs of Strings that will be used as the query params.
   */
  def stream(pairs: (String, String)*): Stream[F, Model] = stream(Query.fromPairs(pairs:_*))
  
  /**
   * Streams ${domainModel}(s) using the specified `query`. Automatically fetches more pages if more elements of the stream are consumed.
   * @param query the query to use when performing the request.
   * @param extraHeaders extra headers to pass when making the request. The `authToken` header is always added.
   */
  def stream(query: Query, extraHeaders: Header.ToRaw*): Stream[F, Model] = stream[Model](pluralName, uri.copy(query = query), extraHeaders:_*)
  
  /**
   * Lists all ${domainModel}(s) using the specified `pairs` as the query params.
   * If the response is paginated <b>all</b> elements will be returned, be careful as this might take a lot of time and memory.
   * @param pairs the pairs of Strings that will be used as the query params.
   */
  def list(pairs: (String, String)*): F[List[Model]] = list(Query.fromPairs(pairs:_*))
  
  /**
   * Lists all ${domainModel}(s) using the specified `query`.
   * If the response is paginated <b>all</b> elements will be returned, be careful as this might take a lot of time and memory.
   * @param query the query to use when performing the request.
   * @param extraHeaders extra headers to pass when making the request. The `authToken` header is always added.
   */
  def list(query: Query, extraHeaders: Header.ToRaw*): F[List[Model]] = list[Model](pluralName, uri.copy(query = query), extraHeaders:_*)
}