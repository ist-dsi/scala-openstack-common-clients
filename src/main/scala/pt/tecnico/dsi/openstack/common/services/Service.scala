package pt.tecnico.dsi.openstack.common.services

import cats.effect.Concurrent
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.instances.string.*
import fs2.{Chunk, Stream}
import io.circe.{Decoder, Encoder, HCursor, Json, Printer}
import org.http4s.Method.{DELETE, GET, PATCH, POST, PUT}
import org.http4s.Status.{Conflict, Gone, NotFound, Successful}
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.client.Client
import org.http4s.{EntityDecoder, EntityEncoder, Header, Method, Request, Response, Uri, circe}
import pt.tecnico.dsi.openstack.common.models.{AuthToken, Link, UnexpectedStatus}

abstract class Service[F[_]](baseUri: Uri, val name: String, protected val authToken: AuthToken)
  (implicit protected val client: Client[F], val F: Concurrent[F]) {
  
  val pluralName = s"${name}s"
  /** The `baseUri` with the `pluraName` appended as an extra path element. */
  val uri: Uri = baseUri / pluralName
  
  // This basically gives us the ability to do POST(value, uri, (authToken +: extraHeaders):_*)
  protected val dsl = new Http4sClientDsl[F] {}
  import dsl.*
  
  protected val jsonPrinter: Printer = Printer.noSpaces.copy(dropNullValues = true)
  protected implicit def jsonEncoder[A: Encoder]: EntityEncoder[F, A] = circe.jsonEncoderWithPrinterOf(jsonPrinter)
  protected implicit def jsonDecoder[A: Decoder]: EntityDecoder[F, A] = circe.accumulatingJsonOf
  
  // Without this decoding to Unit wont work. This makes the EntityDecoder[F, Unit] defined in EntityDecoder companion object
  // have a higher priority than the jsonDecoder defined above. https://github.com/http4s/http4s/issues/2806
  protected implicit val void: EntityDecoder[F, Unit] = EntityDecoder.void
  
  // If Openstack had a normal REST API (without the wrapping) we wouldn't need 80% of these methods.
  
  // The compiler is having difficulty inferring the type when we are doing
  // method.apply(value, uri, (authToken +: extraHeaders):_*)
  private val subjectTokenHeader: Header.ToRaw = authToken
  
  /**
   * Creates a `Decoder` which will decode the response from Json.
   * When `at` is `None` `R` will be decoded directly from the Json root (the normal implementation for most Json REST APIs). Example:
   * {{{
   *  {
   *    "id": "e4d02828-9cac-4765-bf7f-7e210dac7aba",
   *    "zones": 500,
   *    "zone_recordsets": 500
   *  }
   * }}}
   * When `at` is a `Some(x)` `R` will be decoded from the Json object located in the field `x`.
   * For `R` to be correctly parsed in this example `at` should be `Some("quota")`:
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
    jsonDecoder(using at.fold(decoder)(decoder.at))

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
   * In this example `at` was set to `Some("quota")`:
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
  protected def wrapped[R](at: Option[String] = None)(implicit encoder: Encoder[R]): EntityEncoder[F, R] =
    //jsonEncoder(at.fold(encoder)(encoder.at))
    jsonEncoder(using at.fold(encoder) { name =>
      // https://github.com/circe/circe/issues/1536
      encoder.mapJson(originalJson => Json.obj(name -> originalJson))
    })
  
  /**
   * Submits `request` and decodes the response to a `R` on success.
   * @param wrappedAt whether to decode `R` at the Json root, or at the field `at`.
   * @param request the request to execute.
   */
  protected def expectUnwrapped[R: Decoder](wrappedAt: Option[String], request: Request[F]): F[R] =
    implicit val e: EntityDecoder[F, R] = unwrapped(wrappedAt)
    client.expectOr(request)(defaultOnError(request, _))
  
  /**
   * Submits `request` and decodes the response to a `Option[R]` on success.
   * @param wrappedAt whether to decode `R` at the Json root, or at the field `at`.
   * @param request the request to execute.
   */
  protected def expectOptionUnwrapped[R: Decoder](wrappedAt: Option[String], request: Request[F]): F[Option[R]] =
    implicit val e: EntityDecoder[F, R] = unwrapped(wrappedAt)
    client.expectOptionOr(request)(defaultOnError(request, _))
  
  /**
   * Invokes `method` on the specified `uri` passing as body `value`. The response will be parsed to an `R`.
   * @param wrappedAt whether to encode `B` and decode `R` at the Json root, or at the field `at`.
   * @param method the method to use, eg: GET, POST, etc.
   * @param value the value to send in the body. This value will be json encoded using `wrapped`.
   * @param uri the uri to which the request will be made.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   */
  protected def expect[V: Encoder, R: Decoder](wrappedAt: Option[String], method: Method, value: V, uri: Uri, extraHeaders: Header.ToRaw*): F[R] =
    implicit val e: EntityEncoder[F, V] = wrapped(wrappedAt)
    expectUnwrapped(wrappedAt, method.apply(value, uri, (subjectTokenHeader +: extraHeaders) *))
  /**
   * Invokes `method` on the specified `uri` passing as body `value`. The response will be parsed to an `Option[R]`.
   * @param wrappedAt whether to encode `B` and decode `R` at the Json root, or at the field `at`.
   * @param method the method to use, eg: GET, POST, etc.
   * @param value the value to send in the body. This value will be json encoded using `wrapped`.
   * @param uri the uri to which the request will be made.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   */
  protected def expectOption[B: Encoder, R: Decoder](wrappedAt: Option[String], method: Method, value: B, uri: Uri, extraHeaders: Header.ToRaw*)
  : F[Option[R]] =
    implicit val e: EntityEncoder[F, B] = wrapped(wrappedAt)
    expectOptionUnwrapped(wrappedAt, method.apply(value, uri, (subjectTokenHeader +: extraHeaders) *))
  
  /**
   * Invokes `method` on the specified `uri` without any body. The response will be parsed to an `R`.
   * @param wrappedAt whether to decode `R` at the Json root, or at the field `at`.
   * @param method the method to use, eg: GET, POST, etc.
   * @param uri the uri to which the request will be made.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   */
  protected def expect[R: Decoder](wrappedAt: Option[String], method: Method, uri: Uri, extraHeaders: Header.ToRaw*): F[R] =
    expectUnwrapped(wrappedAt, method.apply(uri, (subjectTokenHeader +: extraHeaders) *))
  /**
   * Invokes `method` on the specified `uri` without any body. The response will be parsed to an `Option[R]`.
   * @param wrappedAt whether to decode `R` at the Json root, or at the field `at`.
   * @param method the method to use, eg: GET, POST, etc.
   * @param uri the uri to which the request will be made.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   */
  protected def expectOption[R: Decoder](wrappedAt: Option[String], method: Method, uri: Uri, extraHeaders: Header.ToRaw*): F[Option[R]] =
    expectOptionUnwrapped(wrappedAt, method.apply(uri, (subjectTokenHeader +: extraHeaders) *))
  
  protected def get[R: Decoder](wrappedAt: Option[String], uri: Uri, extraHeaders: Header.ToRaw*): F[R] =
    expect(wrappedAt, GET, uri, extraHeaders *)
  protected def getOption[R: Decoder](wrappedAt: Option[String], uri: Uri, extraHeaders: Header.ToRaw*): F[Option[R]] =
    expectOption(wrappedAt, GET, uri, extraHeaders *)
  
  protected def put[V: Encoder, R: Decoder](wrappedAt: Option[String], value: V, uri: Uri, extraHeaders: Header.ToRaw*): F[R] =
    expect(wrappedAt, PUT, value, uri, extraHeaders *)
  
  protected def patch[V: Encoder, R: Decoder](wrappedAt: Option[String], value: V, uri: Uri, extraHeaders: Header.ToRaw*): F[R] =
    expect(wrappedAt, PATCH, value, uri, extraHeaders *)
  
  protected def post[V: Encoder, R: Decoder](wrappedAt: Option[String], value: V, uri: Uri, extraHeaders: Header.ToRaw*): F[R] =
    expect(wrappedAt, POST, value, uri, extraHeaders *)
  
  protected def postHandleConflict[V: Encoder, R: Decoder](wrappedAt: Option[String], value: V, uri: Uri, extraHeaders: Seq[Header.ToRaw])(onConflict: F[R]): F[R] =
    implicit val d: EntityDecoder[F, R] = unwrapped(wrappedAt)
    implicit val e: EntityEncoder[F, V] = wrapped(wrappedAt)
    val request = POST(value, uri, (subjectTokenHeader +: extraHeaders) *)
    client.run(request).use:
      case Successful(response) => response.as[R]
      case Conflict(_) => onConflict
      case response => defaultOnError(request, response)
  
  type /=>[-T, +R] = PartialFunction[T, R]
  
  protected def postHandleConflictWithError[V: Encoder, R: Decoder, E <: Throwable: Decoder]
  (wrappedAt: Option[String], value: V, uri: Uri, extraHeaders: Seq[Header.ToRaw])(onConflict: E /=> F[R]): F[R] =
    implicit val d: EntityDecoder[F, R] = unwrapped(wrappedAt)
    implicit val e: EntityEncoder[F, V] = wrapped(wrappedAt)
    client.run(POST.apply(value, uri, (subjectTokenHeader +: extraHeaders) *)).use:
      case Successful(response) => response.as[R]
      case response => response.as[E].flatMap:
        case error if response.status == Conflict => onConflict.applyOrElse(error, F.raiseError)
        case error => F.raiseError(error)
  
  /**
   * Invokes a GET request on the specified `uri`, expecting the returned json to be paginated. Automatically fetches more pages
   * if more elements of the stream are consumed.
   * @param wrappedAt the Json object field where `R` will be decoded from.
   * @param uri the uri to which the request will be made. Query params related with pagination will be overwritten after the first request.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   * @tparam R the type of the elements returned.
   */
  protected def stream[R: Decoder](wrappedAt: String, uri: Uri, extraHeaders: Header.ToRaw*): Stream[F, R] =
    implicit val paginatedDecoder: Decoder[(Option[Uri], List[R])] = (c: HCursor) => for
      links <- c.get[List[Link]]("links") orElse c.getOrElse(s"${wrappedAt}_links")(List.empty[Link])
      next = links.collectFirst { case Link("next", uri) => uri }
      objectList <- c.downField(wrappedAt).as[List[R]]
    yield (next, objectList)
    
    Stream.unfoldChunkEval[F, Option[Uri], R](Some(uri)):
      case Some(uri) =>
        client.expect[(Option[Uri], List[R])](GET.apply(uri, (subjectTokenHeader +: extraHeaders) *))
          .map { case (next, entries) => Some((Chunk.iterable(entries), next)) }
      case None => F.pure(None)
  
  /**
   * Invokes a GET request on the specified `uri`, expecting to receive a list of elements. If the response is paginated <b>all</b>
   * elements will be returned, be careful as this might take a lot of time and memory.
   * @param wrappedAt the Json object field where `R` will be decoded from.
   * @param uri the uri to which the request will be made. Query params related with pagination will be overwritten after the first request.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   * @tparam R the type of the elements returned.
   */
  protected def list[R: Decoder](wrappedAt: String, uri: Uri, extraHeaders: Header.ToRaw*): F[List[R]] =
    stream(wrappedAt, uri, extraHeaders *).compile.toList
  
  /**
   * An idempotent delete. If NotFound or Gone are returned this method will succeed.
   * @param uri the uri to which the request will be made.
   * @param extraHeaders extra headers to be used. The `authToken` header is always added.
   */
  protected def delete(uri: Uri, extraHeaders: Header.ToRaw*): F[Unit] =
    val request = DELETE.apply(uri, (subjectTokenHeader +: extraHeaders) *)
    client.run(request).use:
      case Successful(_) | NotFound(_) | Gone(_) => F.unit
      case response => defaultOnError(request, response)
  
  protected def defaultOnError[R](request: Request[F], response: Response[F]): F[R] =
    for
      requestBody: String <- request.bodyText.compile.foldMonoid
      responseBody: String <- response.bodyText.compile.foldMonoid
      // The defaultOnError implemented in the DefaultClient is not very helpful to debug problems
      // Most notably it does not show the body of the response when the request is not successful.
      // https://github.com/http4s/http4s/issues/3707
      // So we created our own UnexpectedStatus with a much more detailed information
      result <- F.raiseError[R](UnexpectedStatus(request.method, request.uri, requestBody, response.status, responseBody))
    yield result
}