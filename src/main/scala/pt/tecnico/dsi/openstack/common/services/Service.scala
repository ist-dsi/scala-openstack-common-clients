package pt.tecnico.dsi.openstack.common.services

import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.{Chunk, Stream}
import io.circe.{Decoder, Encoder, HCursor, Json, Printer}
import org.http4s.Method.{DELETE, GET, PATCH, POST, PUT, PermitsBody}
import org.http4s.Status.{Conflict, Gone, NotFound, Successful}
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

  /**
   * Creates an `EntityDecoder` which will decode the response from Json.
   * When `at` is `None` `R` will be decoded directly from the Json root (the normal implementation for most Json REST APIs). Example:
   * {{{
   *  {
   *    "id": "e4d02828-9cac-4765-bf7f-7e210dac7aba",
   *    "zones": 500,
   *    "zone_recordsets": 500
   *  }
   * }}}
   * When `at` is a `Some(x)` `R` will be decoded from the Json object located in the field `x`.
   * For `R` to be correctly parsed in this example `at` should be Some("quota"):
   * {{{
   *  {
   *    "quota": {
   *      "id": "e4d02828-9cac-4765-bf7f-7e210dac7aba",
   *      "zones": 500,
   *      "zone_recordsets": 500
   *    }
   *  }
   * }}}
   *
   * @param at whether to decode `R` at the Json root, or at the field `at`.
   * @param decoder the circe decoder capable of converting Json to an R.
   */
  protected def unwrapped[R](at: Option[String] = None)(implicit decoder: Decoder[R]): EntityDecoder[F, R] =
    jsonDecoder(at.fold(decoder)(decoder.at))

  /**
   * Creates an `EntityEncoder` which will encode `R` to a Json.
   * When `at` is `None` `R` will be encoded directly (the normal implementation for most Json REST APIs). Example:
   * {{{
   *  {
   *    "id": "e4d02828-9cac-4765-bf7f-7e210dac7aba",
   *    "zones": 500,
   *    "zone_recordsets": 500
   *  }
   * }}}
   * When `at` is a `Some(x)` `R` will be encoded inside a Json object with a single field named `x`.
   * In this example `at` was set to Some("quota"):
   * {{{
   *  {
   *    "quota": {
   *      "id": "e4d02828-9cac-4765-bf7f-7e210dac7aba",
   *      "zones": 500,
   *      "zone_recordsets": 500
   *    }
   *  }
   * }}}
   * @param at whether to encode `R` at the Json root, or at the field `at`.
   * @param encoder the circe encoder capable of converting an R to Json.
   */
  protected def wrapped[R](at: Option[String] = None)(implicit encoder: Encoder[R]): EntityEncoder[F, R] = {
    //jsonEncoder(at.fold(encoder)(encoder.at))
    jsonEncoder(at.fold(encoder) { name =>
      // https://github.com/circe/circe/issues/1536
      encoder.mapJson(originalJson => Json.obj(name -> originalJson))
    })
  }
  
  /**
   * Submits `request` and decodes the response to a `R` on success.
   * @param wrappedAt whether to decode `R` at the Json root, or at the field `at`.
   * @param request the request to execute.
   */
  protected def expectUnwrapped[R: Decoder](wrappedAt: Option[String], request: F[Request[F]]): F[R] =
    client.expect(request)(unwrapped(wrappedAt))
  /**
   * Submits `request` and decodes the response to a `Option[R]` on success.
   * @param wrappedAt whether to decode `R` at the Json root, or at the field `at`.
   * @param request the request to execute.
   */
  protected def expectOptionUnwrapped[R: Decoder](wrappedAt: Option[String], request: F[Request[F]]): F[Option[R]] =
    request.flatMap(client.expectOption(_)(unwrapped(wrappedAt)))

  /**
   * Invokes `method` on the specified `uri` passing as body `value`. The response will be parsed to an `R`.
   * @param wrappedAt whether to encode `B` and decode `R` at the Json root, or at the field `at`.
   * @param method the method to use, eg: GET, POST, etc.
   * @param value the value to send in the body. This value will be json encoded using `wrapped`.
   * @param uri the uri to which the request will be made.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   */
  protected def expect[B: Encoder, R: Decoder](wrappedAt: Option[String], method: Method with PermitsBody, value: B, uri: Uri, extraHeaders: Header*): F[R] = {
    implicit val e: EntityEncoder[F, B] = wrapped(wrappedAt)
    expectUnwrapped(wrappedAt, method.apply(value, uri, (authToken +: extraHeaders):_*))
  }
  /**
   * Invokes `method` on the specified `uri` passing as body `value`. The response will be parsed to an `Option[R]`.
   * @param wrappedAt whether to encode `B` and decode `R` at the Json root, or at the field `at`.
   * @param method the method to use, eg: GET, POST, etc.
   * @param value the value to send in the body. This value will be json encoded using `wrapped`.
   * @param uri the uri to which the request will be made.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   */
  protected def expectOption[B: Encoder, R: Decoder](wrappedAt: Option[String], method: Method with PermitsBody, value: B,
    uri: Uri, extraHeaders: Header*): F[Option[R]] = {
    implicit val e: EntityEncoder[F, B] = wrapped(wrappedAt)
    expectOptionUnwrapped(wrappedAt, method.apply(value, uri, (authToken +: extraHeaders):_*))
  }

  /**
   * Invokes `method` on the specified `uri` without any body. The response will be parsed to an `R`.
   * @param wrappedAt whether to decode `R` at the Json root, or at the field `at`.
   * @param method the method to use, eg: GET, POST, etc.
   * @param uri the uri to which the request will be made.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   */
  protected def expect[R: Decoder](wrappedAt: Option[String], method: Method with PermitsBody, uri: Uri, extraHeaders: Header*): F[R] =
    expectUnwrapped(wrappedAt, method.apply(uri, (authToken +: extraHeaders):_*))
  /**
   * Invokes `method` on the specified `uri` without any body. The response will be parsed to an `Option[R]`.
   * @param wrappedAt whether to decode `R` at the Json root, or at the field `at`.
   * @param method the method to use, eg: GET, POST, etc.
   * @param uri the uri to which the request will be made.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   */
  protected def expectOption[R: Decoder](wrappedAt: Option[String], method: Method with PermitsBody, uri: Uri, extraHeaders: Header*): F[Option[R]] =
    expectOptionUnwrapped(wrappedAt, method.apply(uri, (authToken +: extraHeaders):_*))

  protected def get[R: Decoder](wrappedAt: Option[String], uri: Uri, extraHeaders: Header*): F[R] =
    expect(wrappedAt, GET, uri, extraHeaders:_*)
  protected def getOption[R: Decoder](wrappedAt: Option[String], uri: Uri, extraHeaders: Header*): F[Option[R]] =
    expectOption(wrappedAt, GET, uri, extraHeaders:_*)

  protected def put[V: Encoder, R: Decoder](wrappedAt: Option[String], value: V, uri: Uri, extraHeaders: Header*): F[R] =
    expect(wrappedAt, PUT, value, uri, extraHeaders:_*)

  protected def patch[V: Encoder, R: Decoder](wrappedAt: Option[String], value: V, uri: Uri, extraHeaders: Header*): F[R] =
    expect(wrappedAt, PATCH, value, uri, extraHeaders:_*)

  protected def post[V: Encoder, R: Decoder](wrappedAt: Option[String], value: V, uri: Uri, extraHeaders: Header*): F[R] =
    expect(wrappedAt, POST, value, uri, extraHeaders:_*)

  protected def postHandleConflict[V: Encoder, R: Decoder](wrappedAt: Option[String], value: V, uri: Uri, extraHeaders: Header*)(onConflict: F[R]): F[R] = {
    implicit val d: EntityDecoder[F, R] = unwrapped(wrappedAt)
    implicit val e: EntityEncoder[F, V] = wrapped(wrappedAt)
    POST(value, uri, (authToken +: extraHeaders):_*).flatMap(client.run(_).use {
      case Successful(response) => response.as[R]
      case Conflict(_) => onConflict
      case response => F.raiseError(UnexpectedStatus(response.status))
    })
  }

  /**
   * Invokes a GET request on the specified `uri`, expecting the returned json to be paginated. Automatically fetches more pages
   * if more elements of the stream are consumed.
   * @param wrappedAt the Json object field where `R` will be decoded from.
   * @param uri the uri to which the request will be made.
   * @param query extra query parameters to pass in every request. These are separated from Uri because this method will need to append
   *              query params for the next page/marker/offset.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   * @tparam R the type of the elements returned.
   */
  protected def list[R: Decoder](wrappedAt: String, uri: Uri, query: Query, extraHeaders: Header*): Stream[F, R] = {
    implicit val paginatedDecoder: Decoder[(Option[Uri], List[R])] = (c: HCursor) => for {
      links <- c.get("links")(Link.linksDecoder).orElse(c.getOrElse(s"${wrappedAt}_links")(List.empty[Link])(Link.linksDecoder))
      next = links.collectFirst { case Link("next", uri) => uri}
      objectList <- c.downField(wrappedAt).as[List[R]]
    } yield (next, objectList)

    Stream.unfoldChunkEval[F, Option[Uri], R](Some(uri)) {
      case Some(uri) =>
        for {
          // The new uri query params must have precedence, otherwise we would always be getting the same page/marker/offset
          request <- GET.apply(uri.copy(query = query ++ uri.query.pairs), (authToken +: extraHeaders):_*)
          (next, entries) <- client.expect[(Option[Uri], List[R])](request)
        } yield Some((Chunk.iterable(entries), next))
      case None => F.pure(None)
    }
  }

  /**
   * An idempotent delete. If NotFound or Gone are returned this method will succeed.
   * @param uri the uri to which the request will be made.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   */
  protected def delete(uri: Uri, extraHeaders: Header*): F[Unit] =
    DELETE.apply(uri, (authToken +: extraHeaders):_*).flatMap(client.run(_).use {
      case Successful(_) | NotFound(_) | Gone(_) => F.pure(())
      case response => F.raiseError(UnexpectedStatus(response.status))
    })
}