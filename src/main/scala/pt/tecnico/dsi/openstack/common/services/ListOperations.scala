package pt.tecnico.dsi.openstack.common.services

import fs2.Stream
import io.circe.Decoder
import org.http4s.{Header, Query}

trait ListOperations[F[_], Model] { this: BaseCrudService[F] =>
  implicit val modelDecoder: Decoder[Model]
  
  def stream(pairs: (String, String)*): Stream[F, Model] = stream(Query.fromPairs(pairs:_*))
  def stream(query: Query, extraHeaders: Header*): Stream[F, Model] = stream[Model](pluralName, uri.copy(query = query), extraHeaders:_*)
  
  def list(pairs: (String, String)*): F[List[Model]] = list(Query.fromPairs(pairs:_*))
  def list(query: Query, extraHeaders: Header*): F[List[Model]] = list[Model](pluralName, uri.copy(query = query), extraHeaders:_*)
}