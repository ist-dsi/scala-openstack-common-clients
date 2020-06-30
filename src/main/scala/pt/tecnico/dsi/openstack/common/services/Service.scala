package pt.tecnico.dsi.openstack.common.services

import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.{Chunk, Stream}
import io.circe.{Decoder, Encoder, HCursor, Json, Printer}
import org.http4s.Method.{DELETE, GET, PATCH, POST, PUT, PermitsBody}
import org.http4s.Status.{Gone, NotFound, Successful}
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.{EntityDecoder, EntityEncoder, Header, Method, Query, Request, Uri, circe}
import pt.tecnico.dsi.openstack.common.models.Link

abstract class Service[F[_]](protected val authToken: Header)(implicit protected val client: Client[F], protected val F: Sync[F]) {
  protected val dsl = new Http4sClientDsl[F] {}
  import dsl._

  protected  val jsonPrinter: Printer = Printer.noSpaces.copy(dropNullValues = true)
  protected implicit def jsonEncoder[A: Encoder]: EntityEncoder[F, A] = circe.jsonEncoderWithPrinterOf(jsonPrinter)
  protected implicit def jsonDecoder[A: Decoder]: EntityDecoder[F, A] = circe.accumulatingJsonOf

  // Without this decoding to Unit wont work. This makes the EntityDecoder[F, Unit] defined in EntityDecoder companion object
  // have a higher priority than the jsonDecoder defined above. https://github.com/http4s/http4s/issues/2806
  protected implicit val void: EntityDecoder[F, Unit] = EntityDecoder.void

  protected def unwrapped[R](at: Option[String] = None)(implicit decoder: Decoder[R]): EntityDecoder[F, R] =
    jsonDecoder(at.fold(decoder)(decoder.at))
  protected def wrapped[R](at: Option[String] = None)(implicit encoder: Encoder[R]): EntityEncoder[F, R] =
    jsonEncoder(at.fold(encoder) { name =>
      encoder.mapJson(originalJson => Json.obj(name -> originalJson))
    })

  protected def expectUnwrapped[R: Decoder](request: F[Request[F]], wrappedAt: Option[String]): F[R] =
    client.expect(request)(unwrapped(wrappedAt))

  protected def expect[V: Encoder, R: Decoder](method: Method with PermitsBody, value: V, uri: Uri, wrappedAt: Option[String]): F[R] = {
    implicit val e: EntityEncoder[F, V] = wrapped(wrappedAt)
    expectUnwrapped(method.apply(value, uri, authToken), wrappedAt)
  }
  protected def expect[R: Decoder](method: Method with PermitsBody, uri: Uri, wrappedAt: Option[String]): F[R] =
    expectUnwrapped(method.apply(uri, authToken), wrappedAt)

  protected def get[R: Decoder](uri: Uri, wrappedAt: Option[String]): F[R] =
    expect(GET, uri, wrappedAt)

  protected def put[V: Encoder, R: Decoder](value: V, uri: Uri, wrappedAt: Option[String]): F[R] =
    expect(PUT, value, uri, wrappedAt)

  protected def patch[V: Encoder, R: Decoder](value: V, uri: Uri, wrappedAt: Option[String]): F[R] =
    expect(PATCH, value, uri, wrappedAt)

  protected def post[V: Encoder, R: Decoder](value: V, uri: Uri, wrappedAt: Option[String]): F[R] =
    expect(POST, value, uri, wrappedAt)

  protected def list[R: Decoder](baseKey: String, uri: Uri, query: Query): Stream[F, R] = {
    implicit val paginatedDecoder: Decoder[(Option[Uri], List[R])] = (c: HCursor) => for {
      links <- c.get("links")(Link.linksDecoder).orElse(c.getOrElse(s"${baseKey}_links")(List.empty[Link])(Link.linksDecoder))
      next = links.collectFirst { case Link("next", uri) => uri}
      objectList <- c.downField(baseKey).as[List[R]]
    } yield (next, objectList)

    Stream.unfoldChunkEval[F, Option[Uri], R](Some(uri)) {
      case Some(uri) =>
        for {
          // The new uri query params must have precedence, otherwise we would always be getting the same page/marker/offset
          request <- GET(uri.copy(query = query ++ uri.query.pairs), authToken)
          (next, entries) <- client.expect[(Option[Uri], List[R])](request)
        } yield Some((Chunk.iterable(entries), next))
      case None => F.pure(None)
    }
  }

  protected def delete(uri: Uri): F[Unit] =
    DELETE.apply(uri, authToken).flatMap(client.run(_).use {
      case Successful(_) | NotFound(_) | Gone(_) => F.pure(())
      case response => F.raiseError(UnexpectedStatus(response.status))
    })
}